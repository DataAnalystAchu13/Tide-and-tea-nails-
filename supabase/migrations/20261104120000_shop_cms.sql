-- Website content (CMS), press-on shop and orders.
-- Depends on the base migration for public.is_owner() and public.payments.

-- ---------------------------------------------------------------------------
-- Site content: one row holding everything the owner edits in the app's Website tab
-- (brand, contact, home, about, follow card, sale, shipping, areas, FAQ, sizing, policies, SEO).
-- ---------------------------------------------------------------------------
create table if not exists public.site_settings (
  id int primary key default 1 check (id = 1),
  data jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now()
);
insert into public.site_settings (id) values (1) on conflict do nothing;

-- When the owner taps Publish, a row lands here and the publish-site function rebuilds the website.
create table if not exists public.site_publishes (
  id bigint generated always as identity primary key,
  requested_by uuid default auth.uid(),
  requested_at timestamptz not null default now(),
  status text not null default 'requested' check (status in ('requested', 'dispatched', 'failed')),
  detail text
);

-- ---------------------------------------------------------------------------
-- Catalogue
-- ---------------------------------------------------------------------------
create table if not exists public.products (
  id uuid primary key default gen_random_uuid(),
  slug text unique not null check (slug ~ '^[a-z0-9-]{1,80}$'),
  name text not null check (char_length(name) between 1 and 120),
  description text not null default '',
  price int not null check (price >= 0),
  sale_price int not null default 0 check (sale_price >= 0),
  stock int not null default 0 check (stock >= 0),
  sold int not null default 0,
  made_to_order boolean not null default false,
  exclude_from_sale boolean not null default false,
  shape text not null default '',
  length text not null default '',
  tags text[] not null default '{}',
  sizes text[] not null default '{}',
  images text[] not null default '{}',
  featured boolean not null default false,
  visible boolean not null default true,
  sort int not null default 100,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.posts (
  id uuid primary key default gen_random_uuid(),
  slug text unique not null check (slug ~ '^[a-z0-9-]{1,80}$'),
  title text not null check (char_length(title) between 1 and 160),
  excerpt text not null default '',
  body text not null default '',
  cover text not null default '',
  status text not null default 'draft' check (status in ('draft', 'published')),
  published_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.reviews (
  id uuid primary key default gen_random_uuid(),
  name text not null check (char_length(name) between 1 and 80),
  area text not null default '',
  rating int not null default 5 check (rating between 1 and 5),
  text text not null check (char_length(text) between 1 and 1500),
  images text[] not null default '{}',
  featured boolean not null default false,
  visible boolean not null default true,
  sort int not null default 100,
  created_at timestamptz not null default now()
);

create table if not exists public.coupons (
  code text primary key check (code ~ '^[A-Z0-9]{3,30}$'),
  type text not null check (type in ('percent', 'flat')),
  value int not null check (value > 0),
  min_order int not null default 0,
  max_discount int not null default 0,
  max_uses int not null default 0,          -- 0 = unlimited
  uses int not null default 0,
  expires_at date,
  active boolean not null default true,
  note text not null default '',
  created_at timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- Orders. Created only by the shop edge function (service role), so prices can't be tampered with.
-- ---------------------------------------------------------------------------
create table if not exists public.orders (
  id uuid primary key default gen_random_uuid(),
  code text not null unique,
  customer_uid uuid not null,
  status text not null default 'pending_payment'
    check (status in ('pending_payment', 'payment_submitted', 'paid', 'packed', 'shipped', 'delivered', 'cancelled')),
  mode text not null check (mode in ('razorpay', 'upi')),
  customer jsonb not null,
  notes text not null default '',
  lines jsonb not null,
  subtotal int not null,
  discount int not null default 0,
  coupon text not null default '',
  shipping int not null default 0,
  total int not null check (total > 0),
  upi jsonb not null default '{}'::jsonb,
  razorpay_order_id text unique,
  razorpay_key_id text,
  payment_ref text not null default '',
  payment_method text not null default '',
  oversold boolean not null default false,
  courier text not null default '',
  tracking_number text not null default '',
  tracking_url text not null default '',
  owner_note text not null default '',
  created_at timestamptz not null default now(),
  payment_submitted_at timestamptz,
  paid_at timestamptz,
  shipped_at timestamptz,
  updated_at timestamptz not null default now()
);
-- Shop payments point at their order; one payment row per order.
alter table public.payments
  add constraint payments_order_fk foreign key (order_id) references public.orders (id) on delete set null;
create unique index if not exists payments_one_per_order on public.payments (order_id) where kind = 'shop';

create index if not exists orders_customer_idx on public.orders (customer_uid, created_at desc);
create index if not exists orders_status_idx on public.orders (status, created_at desc);

-- keep updated_at fresh
create or replace function public.touch_updated_at() returns trigger language plpgsql as $$
begin new.updated_at = now(); return new; end $$;
do $$ declare t text; begin
  foreach t in array array['site_settings', 'products', 'posts', 'orders'] loop
    execute format('drop trigger if exists touch_%1$s on public.%1$s; create trigger touch_%1$s before update on public.%1$s for each row execute function public.touch_updated_at();', t);
  end loop;
end $$;

-- ---------------------------------------------------------------------------
-- Row level security
-- ---------------------------------------------------------------------------
alter table public.site_settings enable row level security;
alter table public.site_publishes enable row level security;
alter table public.products enable row level security;
alter table public.posts enable row level security;
alter table public.reviews enable row level security;
alter table public.coupons enable row level security;
alter table public.orders enable row level security;

create policy "site settings are public" on public.site_settings for select using (true);
create policy "owners edit site settings" on public.site_settings for update using (public.is_owner()) with check (public.is_owner());

create policy "owners publish" on public.site_publishes for insert with check (public.is_owner());
create policy "owners see publishes" on public.site_publishes for select using (public.is_owner());

create policy "visible products are public" on public.products for select using (visible or public.is_owner());
create policy "owners manage products" on public.products for all using (public.is_owner()) with check (public.is_owner());

create policy "published posts are public" on public.posts for select using (status = 'published' or public.is_owner());
create policy "owners manage posts" on public.posts for all using (public.is_owner()) with check (public.is_owner());

create policy "visible reviews are public" on public.reviews for select using (visible or public.is_owner());
create policy "owners manage reviews" on public.reviews for all using (public.is_owner()) with check (public.is_owner());

create policy "owners manage coupons" on public.coupons for all using (public.is_owner()) with check (public.is_owner());

create policy "customers see their orders" on public.orders for select using (customer_uid = auth.uid() or public.is_owner());
create policy "owners update orders" on public.orders for update using (public.is_owner()) with check (public.is_owner());

-- ---------------------------------------------------------------------------
-- Marks an order paid exactly once: takes stock, counts the coupon, records the payment.
-- Called by the shop function (Razorpay verify, webhook, owner confirming UPI).
-- ---------------------------------------------------------------------------
create or replace function public.mark_order_paid(p_order uuid, p_method text, p_ref text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  o public.orders;
  l jsonb;
  short boolean := false;
  s int;
begin
  select * into o from public.orders where id = p_order for update;
  if not found then raise exception 'order not found'; end if;
  if o.status not in ('pending_payment', 'payment_submitted') then
    return jsonb_build_object('already', true, 'order', to_jsonb(o));
  end if;

  for l in select * from jsonb_array_elements(o.lines) loop
    select stock into s from public.products
     where id = (l->>'productId')::uuid and not made_to_order for update;
    if found then
      if s < (l->>'qty')::int then short := true; end if;
      update public.products
         set stock = greatest(0, stock - (l->>'qty')::int), sold = sold + (l->>'qty')::int
       where id = (l->>'productId')::uuid;
    end if;
  end loop;

  if o.coupon <> '' then
    update public.coupons set uses = uses + 1 where code = o.coupon;
  end if;

  update public.orders
     set status = 'paid', paid_at = now(), payment_method = p_method, oversold = short,
         payment_ref = coalesce(nullif(p_ref, ''), payment_ref)
   where id = p_order
   returning * into o;

  insert into public.payments (booking_id, order_id, kind, amount, method, reference, name, service)
  values (null, o.id, 'shop', o.total, p_method, o.payment_ref, coalesce(o.customer->>'name', ''), 'Press-on order ' || o.code)
  on conflict do nothing;

  return jsonb_build_object('already', false, 'order', to_jsonb(o));
end $$;
revoke all on function public.mark_order_paid(uuid, text, text) from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- Storage: public bucket for product, blog, review and site photos. Owners upload.
-- ---------------------------------------------------------------------------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('site', 'site', true, 8388608, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do nothing;

create policy "owners upload site images" on storage.objects for insert
  with check (bucket_id = 'site' and public.is_owner());
create policy "owners replace site images" on storage.objects for update
  using (bucket_id = 'site' and public.is_owner());
create policy "owners delete site images" on storage.objects for delete
  using (bucket_id = 'site' and public.is_owner());

-- ---------------------------------------------------------------------------
-- Push the owner's phone when an order is paid, or when a UPI payment needs confirming.
-- ---------------------------------------------------------------------------
create or replace function public.orders_notify() returns trigger
language plpgsql security definer set search_path = public as $$
declare
  items int := coalesce((select sum((l->>'qty')::int) from jsonb_array_elements(new.lines) l), 0);
begin
  if new.status is distinct from old.status then
    if new.status = 'paid' then
      perform public.notify_owners(
        'New press-on order ' || new.code,
        coalesce(new.customer->>'name', 'A customer') || ' · ' || items || ' item(s) · ' || private.money(new.total)
          || case when new.oversold then ' · check stock' else '' end,
        jsonb_build_object('type', 'order', 'bookingId', new.id::text));
    elsif new.status = 'payment_submitted' then
      perform public.notify_owners(
        coalesce(new.customer->>'name', 'A customer') || ' paid for order ' || new.code,
        private.money(new.total) || ' · UPI ref ' || new.payment_ref || '. Tap to confirm.',
        jsonb_build_object('type', 'order', 'bookingId', new.id::text));
    end if;
  end if;
  return new;
end $$;
drop trigger if exists orders_notify on public.orders;
create trigger orders_notify after update of status on public.orders
  for each row execute function public.orders_notify();

-- Customers' "My orders" page and the owner app follow order changes live.
alter publication supabase_realtime add table public.orders;
