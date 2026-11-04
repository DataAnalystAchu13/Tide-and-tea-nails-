-- Tide & Tea Nails: core booking backend on Supabase.
-- Replaces the Firestore collections and the Firestore/Storage security rules.
--
-- Who is who:
--   * Customers sign in anonymously from the website (role "authenticated", auth.uid() = their browser).
--   * Owners sign in with email + password in the Android app and have a row in public.owners.
--
-- Clients only do the minimum; everything that must stay consistent happens here:
--   * slots_taken is maintained from bookings by a trigger (customers never see other bookings).
--   * payments rows are written by a trigger when a booking is confirmed or completed.
--   * chats (the per-booking summary) is maintained by a trigger on messages.
--   * customers submit their UPI reference only through submit_payment().

-- ---------------------------------------------------------------------------
-- Owners and the owner check
-- ---------------------------------------------------------------------------

create table public.owners (
  user_id    uuid primary key references auth.users (id) on delete cascade,
  role       text not null default 'owner',
  created_at timestamptz not null default now()
);

create or replace function public.is_owner()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (select 1 from public.owners o where o.user_id = (select auth.uid()));
$$;

revoke all on function public.is_owner() from public;
grant execute on function public.is_owner() to anon, authenticated;

alter table public.owners enable row level security;
create policy "owners read self" on public.owners
  for select to authenticated using (user_id = (select auth.uid()));

-- Phones signed in to the owner app, for push notifications (Firebase Cloud Messaging tokens).
create table public.push_tokens (
  token      text primary key check (char_length(token) <= 4096),
  user_id    uuid not null references public.owners (user_id) on delete cascade,
  platform   text not null default 'android',
  updated_at timestamptz not null default now()
);
alter table public.push_tokens enable row level security;
create policy "owner manages own tokens" on public.push_tokens
  for all to authenticated
  using (user_id = (select auth.uid()) and (select public.is_owner()))
  with check (user_id = (select auth.uid()) and (select public.is_owner()));

-- ---------------------------------------------------------------------------
-- Price list, slots, studio location, UPI details (one JSON document, id 'main')
-- ---------------------------------------------------------------------------

create table public.config (
  id         text primary key,
  data       jsonb not null,
  updated_at timestamptz not null default now()
);
alter table public.config enable row level security;
create policy "config public read" on public.config for select to anon, authenticated using (true);
create policy "config owner insert" on public.config for insert to authenticated with check ((select public.is_owner()));
create policy "config owner update" on public.config for update to authenticated using ((select public.is_owner())) with check ((select public.is_owner()));
create policy "config owner delete" on public.config for delete to authenticated using ((select public.is_owner()));

-- ---------------------------------------------------------------------------
-- Bookings
-- ---------------------------------------------------------------------------

create table public.bookings (
  id                   uuid primary key default gen_random_uuid(),
  customer_uid         uuid not null default auth.uid(),
  status               text not null default 'requested'
                         check (status in ('requested', 'approved', 'payment_submitted', 'confirmed', 'completed', 'declined')),
  name                 text not null check (char_length(name) between 1 and 100),
  phone                text not null check (char_length(phone) between 1 and 20),
  from_place           text not null default '' check (char_length(from_place) <= 120),
  instagram            text not null default '' check (char_length(instagram) <= 60),
  notes                text not null default '' check (char_length(notes) <= 1000),
  extension            jsonb,
  length               jsonb,
  shape                text not null default '' check (char_length(shape) <= 40),
  add_ons              jsonb not null default '[]' check (jsonb_typeof(add_ons) = 'array'),
  date                 date not null,
  slot                 text not null check (char_length(slot) <= 10),
  service_mode         text not null default 'studio' check (service_mode in ('studio', 'home')),
  location             jsonb,
  distance_km          numeric(7, 1) not null default 0,
  inspo_urls           text[] not null check (cardinality(inspo_urls) between 1 and 10),
  natural_urls         text[] not null default '{}' check (cardinality(natural_urls) <= 5),
  price                jsonb not null check (jsonb_typeof(price) = 'object'),
  owner_note           text not null default '' check (char_length(owner_note) <= 1000),
  payment_ref          text not null default '' check (char_length(payment_ref) <= 40),
  balance_method       text,
  created_at           timestamptz not null default now(),
  updated_at           timestamptz not null default now(),
  approved_at          timestamptz,
  payment_submitted_at timestamptz,
  confirmed_at         timestamptz,
  completed_at         timestamptz
);
create index bookings_customer_idx on public.bookings (customer_uid, created_at desc);
create index bookings_created_idx  on public.bookings (created_at desc);
create index bookings_date_idx     on public.bookings (date);

alter table public.bookings enable row level security;
create policy "bookings read own or owner" on public.bookings
  for select to authenticated
  using (customer_uid = (select auth.uid()) or (select public.is_owner()));
-- Customers create their own booking. Column privileges below decide which fields they may set,
-- so status, owner_note, payment_ref and timestamps always start at their defaults.
create policy "bookings customer insert" on public.bookings
  for insert to authenticated
  with check (customer_uid = (select auth.uid()));
create policy "bookings owner update" on public.bookings
  for update to authenticated
  using ((select public.is_owner())) with check ((select public.is_owner()));
create policy "bookings owner delete" on public.bookings
  for delete to authenticated using ((select public.is_owner()));

revoke insert, update on public.bookings from anon, authenticated;
grant insert (id, name, phone, from_place, instagram, notes, extension, length, shape, add_ons, date, slot,
              service_mode, location, distance_km, inspo_urls, natural_urls, price)
  on public.bookings to authenticated;
-- Only owners pass the update policy; the column list is what the owner app changes.
grant update (status, price, owner_note, payment_ref, balance_method, updated_at,
              approved_at, payment_submitted_at, confirmed_at, completed_at)
  on public.bookings to authenticated;

-- Stamps updated_at and the per-status timestamps, whoever made the change.
create or replace function public.bookings_touch()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at := now();
  if tg_op = 'UPDATE' and new.status is distinct from old.status then
    case new.status
      when 'approved'          then new.approved_at := coalesce(new.approved_at, now());
      when 'payment_submitted' then new.payment_submitted_at := now();
      when 'confirmed'         then new.confirmed_at := now();
      when 'completed'         then new.completed_at := now();
      else null;
    end case;
  end if;
  return new;
end;
$$;
create trigger bookings_touch before insert or update on public.bookings
  for each row execute function public.bookings_touch();

-- The customer's only change after approval: send the UPI reference of their advance.
create or replace function public.submit_payment(p_booking_id uuid, p_ref text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  ref text := btrim(coalesce(p_ref, ''));
begin
  if char_length(ref) < 4 or char_length(ref) > 40 then
    raise exception 'Payment reference must be 4 to 40 characters' using errcode = '22023';
  end if;
  update public.bookings
     set status = 'payment_submitted', payment_ref = ref
   where id = p_booking_id
     and customer_uid = (select auth.uid())
     and status = 'approved';
  if not found then
    raise exception 'Booking not found or not awaiting payment' using errcode = '42501';
  end if;
end;
$$;
revoke all on function public.submit_payment(uuid, text) from public;
grant execute on function public.submit_payment(uuid, text) to authenticated;

-- ---------------------------------------------------------------------------
-- Taken slots: what the website shows as unavailable. No customer details.
-- ---------------------------------------------------------------------------

create table public.slots_taken (
  booking_id uuid primary key references public.bookings (id) on delete cascade,
  date       date not null,
  slot       text not null
);
create index slots_taken_date_idx on public.slots_taken (date);
alter table public.slots_taken enable row level security;
create policy "slots public read" on public.slots_taken for select to anon, authenticated using (true);
revoke insert, update, delete on public.slots_taken from anon, authenticated;

create or replace function public.bookings_sync_slot()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if new.status in ('approved', 'payment_submitted', 'confirmed', 'completed') then
    insert into public.slots_taken (booking_id, date, slot) values (new.id, new.date, new.slot)
    on conflict (booking_id) do update set date = excluded.date, slot = excluded.slot;
  else
    delete from public.slots_taken where booking_id = new.id;
  end if;
  return null;
end;
$$;
create trigger bookings_sync_slot after insert or update of status, date, slot on public.bookings
  for each row execute function public.bookings_sync_slot();

-- ---------------------------------------------------------------------------
-- Payments (advance on confirm, balance on completion; shop orders add kind 'shop')
-- ---------------------------------------------------------------------------

create table public.payments (
  id         uuid primary key default gen_random_uuid(),
  booking_id uuid references public.bookings (id) on delete set null,
  order_id   uuid,
  name       text not null default '',
  amount     integer not null check (amount >= 0),
  kind       text not null check (kind in ('advance', 'balance', 'shop')),
  method     text not null default '',
  reference  text not null default '',
  service    text not null default '',
  created_at timestamptz not null default now()
);
create unique index payments_booking_kind_uq on public.payments (booking_id, kind) where booking_id is not null;
create index payments_created_idx on public.payments (created_at desc);
alter table public.payments enable row level security;
create policy "payments owner all" on public.payments
  for all to authenticated using ((select public.is_owner())) with check ((select public.is_owner()));

create or replace function public.bookings_record_payment()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  total   integer := coalesce((new.price ->> 'total')::numeric, 0)::integer;
  advance integer := coalesce((new.price ->> 'advance')::numeric, 0)::integer;
begin
  if new.status = old.status then
    return null;
  end if;
  if new.status = 'confirmed' and advance > 0 then
    insert into public.payments (booking_id, name, amount, kind, method, reference, service)
    values (new.id, new.name, advance, 'advance', 'UPI', new.payment_ref, coalesce(new.extension ->> 'name', ''))
    on conflict (booking_id, kind) where booking_id is not null do nothing;
  elsif new.status = 'completed' and total - advance > 0 then
    insert into public.payments (booking_id, name, amount, kind, method, reference, service)
    values (new.id, new.name, total - advance, 'balance', coalesce(new.balance_method, 'Cash'), '', coalesce(new.extension ->> 'name', ''))
    on conflict (booking_id, kind) where booking_id is not null do nothing;
  end if;
  return null;
end;
$$;
create trigger bookings_record_payment after update of status on public.bookings
  for each row execute function public.bookings_record_payment();

-- ---------------------------------------------------------------------------
-- Chat: one conversation per booking
-- ---------------------------------------------------------------------------

create table public.chats (
  booking_id      uuid primary key references public.bookings (id) on delete cascade,
  customer_uid    uuid not null,
  name            text not null default '',
  last_text       text not null default '',
  last_from       text not null default '',
  last_at         timestamptz not null default now(),
  owner_unread    integer not null default 0,
  customer_unread integer not null default 0
);
create index chats_last_at_idx on public.chats (last_at desc);
alter table public.chats enable row level security;
create policy "chats read client or owner" on public.chats
  for select to authenticated
  using (customer_uid = (select auth.uid()) or (select public.is_owner()));
revoke insert, update, delete on public.chats from anon, authenticated;

create table public.messages (
  id         uuid primary key default gen_random_uuid(),
  booking_id uuid not null references public.bookings (id) on delete cascade,
  sender     text not null check (sender in ('owner', 'customer')),
  text       text not null default '' check (char_length(text) <= 2000),
  media_url  text check (media_url is null or char_length(media_url) <= 1000),
  media_type text check (media_type in ('image', 'video')),
  created_at timestamptz not null default now(),
  check ((media_url is null) = (media_type is null)),
  check (char_length(text) > 0 or media_url is not null)
);
create index messages_booking_idx on public.messages (booking_id, created_at);
alter table public.messages enable row level security;

create or replace function public.is_booking_client(p_booking_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (select 1 from public.bookings b where b.id = p_booking_id and b.customer_uid = (select auth.uid()));
$$;
revoke all on function public.is_booking_client(uuid) from public;
grant execute on function public.is_booking_client(uuid) to authenticated;

create policy "messages read client or owner" on public.messages
  for select to authenticated
  using ((select public.is_owner()) or public.is_booking_client(booking_id));
create policy "messages send" on public.messages
  for insert to authenticated
  with check (
    (sender = 'owner' and (select public.is_owner()))
    or (sender = 'customer' and public.is_booking_client(booking_id))
  );
create policy "messages owner delete" on public.messages
  for delete to authenticated using ((select public.is_owner()));
revoke update on public.messages from anon, authenticated;
revoke insert on public.messages from anon, authenticated;
grant insert (booking_id, sender, text, media_url, media_type) on public.messages to authenticated;

create or replace function public.messages_update_chat()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  preview text := case
    when char_length(new.text) > 0 then left(new.text, 140)
    when new.media_type = 'video' then 'Video'
    else 'Photo'
  end;
begin
  insert into public.chats as c (booking_id, customer_uid, name, last_text, last_from, last_at, owner_unread, customer_unread)
  select b.id, b.customer_uid, b.name, preview, new.sender, new.created_at,
         case when new.sender = 'customer' then 1 else 0 end,
         case when new.sender = 'owner' then 1 else 0 end
    from public.bookings b where b.id = new.booking_id
  on conflict (booking_id) do update set
    last_text       = excluded.last_text,
    last_from       = excluded.last_from,
    last_at         = excluded.last_at,
    owner_unread    = case when new.sender = 'customer' then c.owner_unread + 1 else 0 end,
    customer_unread = case when new.sender = 'owner' then c.customer_unread + 1 else 0 end;
  return null;
end;
$$;
create trigger messages_update_chat after insert on public.messages
  for each row execute function public.messages_update_chat();

-- Clears the unread badge for whoever is reading.
create or replace function public.mark_chat_read(p_booking_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if public.is_owner() then
    update public.chats set owner_unread = 0 where booking_id = p_booking_id and owner_unread <> 0;
  elsif public.is_booking_client(p_booking_id) then
    update public.chats set customer_unread = 0 where booking_id = p_booking_id and customer_unread <> 0;
  end if;
end;
$$;
revoke all on function public.mark_chat_read(uuid) from public;
grant execute on function public.mark_chat_read(uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- Photo and video storage
-- Buckets are public-read: files live under random UUID paths, the same model as
-- Firebase download URLs. Listing files and uploading are limited by the policies below.
-- ---------------------------------------------------------------------------

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
  ('bookings', 'bookings', true, 12 * 1024 * 1024, array['image/*']),
  ('chats',    'chats',    true, 60 * 1024 * 1024, array['image/*', 'video/*'])
on conflict (id) do update set
  public = excluded.public,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

-- bookings/{customerUid}/{bookingId}/{inspo_1.jpg | natural_1.jpg ...}
create policy "booking photos upload own folder" on storage.objects
  for insert to authenticated
  with check (bucket_id = 'bookings' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "booking photos read own or owner" on storage.objects
  for select to authenticated
  using (bucket_id = 'bookings' and ((storage.foldername(name))[1] = (select auth.uid())::text or (select public.is_owner())));

-- chats/{bookingId}/{messageId}.{jpg|mp4}
create or replace function public.can_use_chat_folder(p_name text)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  folder text := (storage.foldername(p_name))[1];
begin
  if public.is_owner() then
    return true;
  end if;
  if folder !~ '^[0-9a-fA-F-]{36}$' then
    return false;
  end if;
  return public.is_booking_client(folder::uuid);
end;
$$;
revoke all on function public.can_use_chat_folder(text) from public;
grant execute on function public.can_use_chat_folder(text) to authenticated;

create policy "chat media upload" on storage.objects
  for insert to authenticated
  with check (bucket_id = 'chats' and public.can_use_chat_folder(name));
create policy "chat media read" on storage.objects
  for select to authenticated
  using (bucket_id = 'chats' and public.can_use_chat_folder(name));
create policy "media owner delete" on storage.objects
  for delete to authenticated
  using (bucket_id in ('bookings', 'chats') and (select public.is_owner()));

-- ---------------------------------------------------------------------------
-- Live updates for the website and the owner app (Realtime applies the RLS above)
-- ---------------------------------------------------------------------------

alter publication supabase_realtime add table
  public.bookings, public.slots_taken, public.config, public.chats, public.messages, public.payments;

-- ---------------------------------------------------------------------------
-- Push notifications to the owner app
-- public.notify_owners(title, body, data) posts to the notify-owners Edge Function,
-- which sends a Firebase Cloud Messaging push to every phone in push_tokens.
-- Other migrations (shop orders) call it from their own triggers.
-- ---------------------------------------------------------------------------

create extension if not exists pg_net with schema extensions;

create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

create table private.app_settings (
  key   text primary key,
  value text not null
);
-- Shared secret between the database and the notify-owners function. Generated here, never leaves Supabase.
insert into private.app_settings (key, value)
values ('notify_secret', encode(extensions.gen_random_bytes(32), 'hex'))
on conflict (key) do nothing;
-- 'project_url' (https://<ref>.supabase.co) is added by a later migration once the project exists.

create or replace function public.check_notify_secret(p_secret text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (select 1 from private.app_settings where key = 'notify_secret' and value = p_secret);
$$;
revoke all on function public.check_notify_secret(text) from public, anon, authenticated;
grant execute on function public.check_notify_secret(text) to service_role;

create or replace function public.notify_owners(p_title text, p_body text, p_data jsonb default '{}'::jsonb)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  url    text;
  secret text;
begin
  select value into url from private.app_settings where key = 'project_url';
  select value into secret from private.app_settings where key = 'notify_secret';
  if url is null or secret is null or not exists (select 1 from public.push_tokens) then
    return;
  end if;
  perform net.http_post(
    url := rtrim(url, '/') || '/functions/v1/notify-owners',
    body := jsonb_build_object('title', left(p_title, 120), 'body', left(p_body, 240), 'data', coalesce(p_data, '{}'::jsonb)),
    headers := jsonb_build_object('Content-Type', 'application/json', 'x-notify-secret', secret),
    timeout_milliseconds := 8000
  );
exception when others then
  -- A notification must never block a booking, a payment or a message.
  raise warning 'notify_owners failed: %', sqlerrm;
end;
$$;
revoke all on function public.notify_owners(text, text, jsonb) from public, anon, authenticated;

create or replace function private.money(n numeric)
returns text
language sql
immutable
as $$ select '₹' || to_char(coalesce(n, 0), 'FM99,99,99,999') $$;

create or replace function public.bookings_notify()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if tg_op = 'INSERT' then
    perform public.notify_owners(
      'New request from ' || coalesce(nullif(new.name, ''), 'a client'),
      coalesce(new.extension ->> 'name', 'Nail set') || ' · ' || to_char(new.date, 'Dy DD Mon') || ' at ' || new.slot
        || ' · ' || private.money((new.price ->> 'total')::numeric),
      jsonb_build_object('type', 'booking', 'bookingId', new.id::text));
  elsif new.status = 'payment_submitted' and old.status is distinct from new.status then
    perform public.notify_owners(
      new.name || ' paid the advance',
      private.money((new.price ->> 'advance')::numeric) || ' · UPI ref ' || new.payment_ref || '. Tap to confirm.',
      jsonb_build_object('type', 'booking', 'bookingId', new.id::text));
  end if;
  return null;
end;
$$;
create trigger bookings_notify after insert or update of status on public.bookings
  for each row execute function public.bookings_notify();

create or replace function public.messages_notify()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  who text;
begin
  if new.sender <> 'customer' then
    return null;
  end if;
  select name into who from public.bookings where id = new.booking_id;
  perform public.notify_owners(
    coalesce(nullif(who, ''), 'A client'),
    case
      when new.media_type = 'video' then 'Sent a video'
      when new.media_type = 'image' then coalesce(nullif(left(new.text, 140), ''), 'Sent a photo')
      else left(new.text, 140)
    end,
    jsonb_build_object('type', 'chat', 'bookingId', new.booking_id::text));
  return null;
end;
$$;
create trigger messages_notify after insert on public.messages
  for each row execute function public.messages_notify();
