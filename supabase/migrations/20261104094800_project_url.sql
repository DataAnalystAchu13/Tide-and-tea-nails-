-- Where public.notify_owners() reaches the notify-owners Edge Function.
insert into private.app_settings (key, value)
values ('project_url', 'https://zsfutzfgfxskxgggcfjr.supabase.co')
on conflict (key) do update set value = excluded.value;
