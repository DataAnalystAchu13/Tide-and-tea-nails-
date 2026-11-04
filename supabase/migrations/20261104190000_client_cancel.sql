-- Clients can cancel their own booking from the website.
--  * New status 'cancelled' (by the client), separate from 'declined' (by the owner).
--  * The slot frees itself: bookings_sync_slot only holds approved/paid/confirmed/completed.
--  * Refund rule from the policy: a paid advance is refunded when the client cancels at least
--    24 hours before the slot (India time); later than that the advance is kept.
--  * The owner gets a notification saying whether a refund is due.

alter table public.bookings drop constraint if exists bookings_status_check;
alter table public.bookings add constraint bookings_status_check
  check (status in ('requested', 'approved', 'payment_submitted', 'confirmed', 'completed', 'declined', 'cancelled'));

alter table public.bookings add column if not exists cancelled_at timestamptz;
alter table public.bookings add column if not exists refund_due boolean not null default false;

create or replace function public.cancel_booking(p_booking_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  b public.bookings%rowtype;
  starts timestamptz;
  paid boolean;
  refund boolean;
  amount numeric;
begin
  select * into b from public.bookings
   where id = p_booking_id and customer_uid = (select auth.uid())
   for update;
  if not found or b.status not in ('requested', 'approved', 'payment_submitted', 'confirmed') then
    raise exception 'Booking not found or can no longer be cancelled' using errcode = '42501';
  end if;

  starts := (b.date + coalesce(nullif(b.slot, ''), '00:00')::time) at time zone 'Asia/Kolkata';
  if starts <= now() then
    raise exception 'This appointment has already started' using errcode = '22023';
  end if;

  paid := b.status in ('payment_submitted', 'confirmed');
  refund := paid and starts - now() >= interval '24 hours';
  amount := coalesce((b.price ->> 'advance')::numeric, 0);

  update public.bookings
     set status = 'cancelled', cancelled_at = now(), refund_due = refund
   where id = b.id;

  perform public.notify_owners(
    coalesce(nullif(b.name, ''), 'A client') || ' cancelled',
    coalesce(b.extension ->> 'name', 'Nail set') || ' · ' || to_char(b.date, 'Dy DD Mon') || ' at ' || b.slot
      || case when refund then ' · Refund ' || private.money(amount) || ' from your UPI app'
              when paid then ' · Under 24 hours: advance kept'
              else ' · Not paid, slot is free again' end,
    jsonb_build_object('type', 'booking', 'bookingId', b.id::text));

  return jsonb_build_object('paid', paid, 'refund', refund, 'amount', amount);
end;
$$;
revoke all on function public.cancel_booking(uuid) from public;
grant execute on function public.cancel_booking(uuid) to authenticated;

-- The owner app clears refund_due once she has sent the money back (the update policy is owner-only).
grant update (refund_due) on public.bookings to authenticated;
