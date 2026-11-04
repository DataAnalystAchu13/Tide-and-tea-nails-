-- Akshaya's "About me" letter and photo for the website's About page.
-- Only replaces the original placeholder story, never wording the owner already wrote in the app.
update public.site_settings
set data = jsonb_set(
      coalesce(data, '{}'::jsonb),
      '{about}',
      coalesce(data->'about', '{}'::jsonb) || '{"title": "hii girlies, *I''m Akshaya!* 🎀🤎", "body": "My love for nails started when I was just a little girl. I used to find random things around the house and turn them into little tools to create my own nail art. Little did I know that this tiny childhood obsession would one day turn into something so special to me. 🥹\n\nAnd that''s how Tide and Tea Nails was born! ✨\n\nEvery booking, every order, every little bit of love and every person who trusts me with their nails means more than words can explain. You''re not just booking a nail appointment; you''re helping me get one little step closer to a dream I''ve held onto for years.\n\nThank you for being here, supporting my little dream and letting me be a tiny part of your special moments. Here''s to more pretty nails, happy clients and lots of love! 💌", "signoff": "With love,", "signature": "Akshaya ♡", "photo": "/akshaya.webp", "caption": "the girl behind the nails"}'::jsonb
    ),
    updated_at = now()
where id = 1
  and (
    coalesce(data->'about'->>'body', '') = ''
    or data->'about'->>'body' like 'Tide & Tea Nails is a Chennai nail studio%'
  );
