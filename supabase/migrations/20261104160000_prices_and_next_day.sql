-- Nov 2026 price and booking-rule update (requested by the owner).
--  * New extension and add-on prices; no extra charge for length; painted art and 3D charms have no fixed price.
--  * Travel is no longer priced by distance: the site shows a note and the owner confirms it.
--  * Open Monday to Sunday.
--  * Advance payments go to UPI ID 9043450513@upi.
--  * Bookings can only be made from the next day (India time); same-day requests are refused.

update public.config
set data = data || $json${
  "upiId": "9043450513@upi",
  "extensionTypes": [
    {
      "id": "softgel",
      "name": "Soft gel tips",
      "price": 1500,
      "description": "Light and natural feeling. Lasts 3 to 4 weeks."
    },
    {
      "id": "acrylic",
      "name": "Acrylic extensions",
      "price": 1900,
      "description": "The strongest option. Best for long and dramatic shapes."
    },
    {
      "id": "polygel",
      "name": "PolyGel extensions",
      "price": 2000,
      "description": "Lightweight and flexible with almost no smell."
    },
    {
      "id": "biab",
      "name": "Gel overlay on natural nails",
      "price": 1000,
      "description": "Strengthens your own nails. No tips added."
    },
    {
      "id": "pressons",
      "name": "Custom press-on designs",
      "price": 400,
      "description": "Starting at ₹400 + shipping. Made to your size, reusable at home."
    }
  ],
  "lengths": [
    {
      "id": "short",
      "name": "Short",
      "price": 0
    },
    {
      "id": "medium",
      "name": "Medium",
      "price": 0
    },
    {
      "id": "long",
      "name": "Long",
      "price": 0
    },
    {
      "id": "xl",
      "name": "Extra long",
      "price": 0
    }
  ],
  "addOns": [
    {
      "id": "french",
      "name": "French tips",
      "price": 300
    },
    {
      "id": "chrome",
      "name": "Chrome finish",
      "price": 300
    },
    {
      "id": "cateye",
      "name": "Cat-eye finish",
      "price": 300
    },
    {
      "id": "gelremoval",
      "name": "Gel polish removal",
      "price": 300
    },
    {
      "id": "removal",
      "name": "Nail extension removal",
      "price": 500
    },
    {
      "id": "art",
      "name": "Painted nail art",
      "price": 0
    },
    {
      "id": "charms",
      "name": "3D charms and gems",
      "price": 0
    }
  ],
  "travel": {
    "note": "Travel charges will be applicable for home visits. Please contact the owner to confirm the travel charges.",
    "slabs": [],
    "perKmBeyond": 0
  },
  "workingDays": [
    1,
    2,
    3,
    4,
    5,
    6,
    7
  ]
}$json$::jsonb
where id = 'main';

-- No same-day bookings. Only new requests are checked, so the owner can still move or edit existing ones.
create or replace function public.bookings_next_day_only()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if new.date <= (now() at time zone 'Asia/Kolkata')::date then
    raise exception 'Appointments can be booked from tomorrow onwards.' using errcode = 'check_violation';
  end if;
  return new;
end;
$$;

drop trigger if exists bookings_next_day_only on public.bookings;
create trigger bookings_next_day_only before insert on public.bookings
  for each row execute function public.bookings_next_day_only();
