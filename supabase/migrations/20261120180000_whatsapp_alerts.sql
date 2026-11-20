-- WhatsApp alerts for the owner.
-- notify_owners() used to stop when no phone had the owner app's push token, so nothing reached
-- the notify-owners function. It now always calls the function, which sends the app push (if any
-- phones are signed in) and a WhatsApp message to the owner for bookings and orders (if the
-- WhatsApp secrets are set). pg_net is asynchronous, so bookings and payments never wait on it.
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
  if url is null or secret is null then
    return;
  end if;
  perform net.http_post(
    url := rtrim(url, '/') || '/functions/v1/notify-owners',
    body := jsonb_build_object('title', left(p_title, 120), 'body', left(p_body, 240), 'data', coalesce(p_data, '{}'::jsonb)),
    headers := jsonb_build_object('Content-Type', 'application/json', 'x-notify-secret', secret),
    timeout_milliseconds := 15000
  );
exception when others then
  -- A notification must never block a booking, a payment or a message.
  raise warning 'notify_owners failed: %', sqlerrm;
end;
$$;
revoke all on function public.notify_owners(text, text, jsonb) from public, anon, authenticated;
