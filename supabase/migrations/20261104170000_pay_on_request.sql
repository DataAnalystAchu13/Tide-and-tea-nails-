-- Customers pay the advance straight after booking, without waiting for the owner's approval.
--  * submit_payment() now accepts a booking that is still 'requested' (as well as 'approved').
--  * Paying holds the slot (bookings_sync_slot already holds it from 'payment_submitted').
--  * If someone else paid for the same slot first, the owner's notification says so,
--    so she can move one of them or refund.
-- The owner still confirms each payment in the app, and can decline and refund if the set isn't possible.

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
     and status in ('requested', 'approved');
  if not found then
    raise exception 'Booking not found or not awaiting payment' using errcode = '42501';
  end if;
end;
$$;
revoke all on function public.submit_payment(uuid, text) from public;
grant execute on function public.submit_payment(uuid, text) to authenticated;

create or replace function public.bookings_notify()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  clash boolean;
begin
  if tg_op = 'INSERT' then
    perform public.notify_owners(
      'New request from ' || coalesce(nullif(new.name, ''), 'a client'),
      coalesce(new.extension ->> 'name', 'Nail set') || ' · ' || to_char(new.date, 'Dy DD Mon') || ' at ' || new.slot
        || ' · ' || private.money((new.price ->> 'total')::numeric) || ' · waiting for advance',
      jsonb_build_object('type', 'booking', 'bookingId', new.id::text));
  elsif new.status = 'payment_submitted' and old.status is distinct from new.status then
    select exists (
      select 1 from public.slots_taken t
       where t.date = new.date and t.slot = new.slot and t.booking_id <> new.id
    ) into clash;
    perform public.notify_owners(
      new.name || ' paid the advance',
      private.money((new.price ->> 'advance')::numeric) || ' · UPI ref ' || new.payment_ref
        || case when clash then ' · SLOT CLASH: someone else holds ' || to_char(new.date, 'DD Mon') || ' ' || new.slot
                else '. Slot held. Tap to confirm.' end,
      jsonb_build_object('type', 'booking', 'bookingId', new.id::text));
  end if;
  return null;
end;
$$;
