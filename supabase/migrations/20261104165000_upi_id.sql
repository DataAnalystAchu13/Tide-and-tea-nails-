-- The owner's UPI ID for advance payments. Replaces the seed placeholder only,
-- so a UPI ID the owner later sets in the app is never overwritten.
update public.config
set data = jsonb_set(data, '{upiId}', '"9043450513@upi"'::jsonb)
where id = 'main'
  and coalesce(data->>'upiId', '') in ('', 'yourname@upi');
