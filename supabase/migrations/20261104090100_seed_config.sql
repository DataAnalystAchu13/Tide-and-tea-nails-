-- Starter price list, slots, studio location and UPI details.
-- Placeholders: edit them in the owner app or in Table Editor > config > main.
insert into public.config (id, data) values ('main', $json$
{
  "currency": "₹",
  "extensionTypes": [
    {
      "id": "softgel",
      "name": "Soft gel tips (Gel-X)",
      "price": 1800,
      "description": "Light and natural feeling. Lasts 3 to 4 weeks."
    },
    {
      "id": "acrylic",
      "name": "Acrylic",
      "price": 1500,
      "description": "The strongest option. Best for long and dramatic shapes."
    },
    {
      "id": "polygel",
      "name": "PolyGel",
      "price": 2000,
      "description": "Lightweight and flexible with almost no smell."
    },
    {
      "id": "biab",
      "name": "Builder gel overlay (BIAB)",
      "price": 1200,
      "description": "Strengthens your own nails. No tips added."
    },
    {
      "id": "pressons",
      "name": "Custom press-ons",
      "price": 1000,
      "description": "Made to your size. Reusable at home."
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
      "price": 200
    },
    {
      "id": "long",
      "name": "Long",
      "price": 400
    },
    {
      "id": "xl",
      "name": "Extra long",
      "price": 700
    }
  ],
  "shapes": [
    "Almond",
    "Oval",
    "Square",
    "Squoval",
    "Coffin",
    "Stiletto",
    "Not sure yet"
  ],
  "addOns": [
    {
      "id": "french",
      "name": "French tips",
      "price": 200
    },
    {
      "id": "chrome",
      "name": "Chrome finish",
      "price": 300
    },
    {
      "id": "cateye",
      "name": "Cat-eye",
      "price": 300
    },
    {
      "id": "art",
      "name": "Hand-painted art (up to 4 nails)",
      "price": 400
    },
    {
      "id": "charms",
      "name": "3D charms and gems",
      "price": 350
    },
    {
      "id": "removal",
      "name": "Removal of old extensions",
      "price": 300
    }
  ],
  "travel": {
    "note": "Home visits are charged by distance from the studio.",
    "slabs": [
      {
        "upToKm": 3,
        "charge": 0
      },
      {
        "upToKm": 8,
        "charge": 150
      },
      {
        "upToKm": 15,
        "charge": 300
      }
    ],
    "perKmBeyond": 25
  },
  "studio": {
    "name": "Tide & Tea studio",
    "address": "Studio address to be added",
    "lat": 13.0827,
    "lng": 80.2707
  },
  "slots": [
    "10:00",
    "12:30",
    "15:00",
    "17:30"
  ],
  "workingDays": [
    2,
    3,
    4,
    5,
    6,
    7
  ],
  "blockedDates": [],
  "bookingWindowDays": 30,
  "advancePercent": 30,
  "upiId": "yourname@upi",
  "upiName": "Tide and Tea Nails",
  "whatsapp": ""
}
$json$::jsonb)
on conflict (id) do nothing;
