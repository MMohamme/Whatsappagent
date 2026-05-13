from __future__ import annotations

import argparse
from typing import Iterable

try:
    from .database import (
        AutoMode,
        Contact,
        ContactCategory,
        ContactRule,
        EventTargetType,
        EventTicket,
        Note,
        NoteScope,
        RelationType,
        SessionLocal,
        init_db,
    )
except ImportError:
    from database import (
        AutoMode,
        Contact,
        ContactCategory,
        ContactRule,
        EventTargetType,
        EventTicket,
        Note,
        NoteScope,
        RelationType,
        SessionLocal,
        init_db,
    )


CONTACT_SEEDS = [
    {
        "display_name": "Meine Mutter",
        "phone_number": None,
        "relation_type": RelationType.CORE_FAMILY.value,
        "specific_relation": "Mutter",
        "preferred_lang": "Arabisch (syrischer Dialekt)",
        "auto_mode": AutoMode.AUTO_LOW_RISK.value,
        "categories": ["CORE_FAMILY", "EXTENDED_FAMILY"],
        "style": (
            "Sehr warm, respektvoll und liebevoll. Nutze passende syrisch-arabische "
            "Anreden wie amy, habibti oder ya rohi. Bei ernsten Themen beruhigend "
            "und geduldig antworten."
        ),
    },
    {
        "display_name": "Mein Vater",
        "phone_number": None,
        "relation_type": RelationType.CORE_FAMILY.value,
        "specific_relation": "Vater",
        "preferred_lang": "Arabisch (syrischer Dialekt)",
        "auto_mode": AutoMode.AUTO_LOW_RISK.value,
        "categories": ["CORE_FAMILY", "EXTENDED_FAMILY"],
        "style": "Respektvoll, klar, kurz und verlaesslich. Wenige Emojis, ruhiger Ton.",
    },
    {
        "display_name": "Mohammed",
        "phone_number": None,
        "relation_type": RelationType.FRIEND.value,
        "specific_relation": "Freund",
        "preferred_lang": "Marokkanisch",
        "auto_mode": AutoMode.AUTO_LOW_RISK.value,
        "categories": ["FRIEND"],
        "style": "Locker, humorvoll und entspannt. Anrede: muealim.",
    },
    {
        "display_name": "Mazen",
        "phone_number": None,
        "relation_type": RelationType.FRIEND.value,
        "specific_relation": "Freund",
        "preferred_lang": "Arabisch (syrischer Dialekt)",
        "auto_mode": AutoMode.AUTO_LOW_RISK.value,
        "categories": ["FRIEND"],
        "style": "Locker, humorvoll und entspannt. Anrede: muealim.",
    },
    {
        "display_name": "Mouaz",
        "phone_number": None,
        "relation_type": RelationType.FRIEND.value,
        "specific_relation": "Freund",
        "preferred_lang": "Arabisch (syrischer Dialekt)",
        "auto_mode": AutoMode.AUTO_LOW_RISK.value,
        "categories": ["FRIEND"],
        "style": "Locker, humorvoll und entspannt. Anrede: muealim.",
    },
    {
        "display_name": "Azhar",
        "phone_number": None,
        "relation_type": RelationType.FRIEND.value,
        "specific_relation": "Freund",
        "preferred_lang": "Englisch",
        "auto_mode": AutoMode.AUTO_LOW_RISK.value,
        "categories": ["FRIEND"],
        "style": "Casual and funny. Address as Bro.",
    },
    {
        "display_name": "Mouaz Tuerkei",
        "phone_number": None,
        "relation_type": RelationType.CORE_FAMILY.value,
        "specific_relation": "Bruder",
        "preferred_lang": "Arabisch (syrischer Dialekt)",
        "auto_mode": AutoMode.AUTO_LOW_RISK.value,
        "categories": ["CORE_FAMILY"],
        "style": "Locker, humorvoll und bruederlich. Anrede: muealim.",
    },
    {
        "display_name": "Tatjana Wydlok(Maxi)",
        "phone_number": None,
        "relation_type": RelationType.WORK.value,
        "specific_relation": "Vorgesetzte",
        "preferred_lang": "Deutsch",
        "auto_mode": AutoMode.REVIEW.value,
        "categories": ["WORK"],
        "style": "Professionell, sachlich und kurz. Bei Arbeitsanfragen nie automatisch zusagen.",
    },
    {
        "display_name": "scheni",
        "phone_number": None,
        "relation_type": RelationType.WORK.value,
        "specific_relation": "Chefin",
        "preferred_lang": "Deutsch",
        "auto_mode": AutoMode.REVIEW.value,
        "categories": ["WORK"],
        "style": "Locker, aber professionell und sachlich. Bei Arbeitsanfragen Review.",
    },
    {
        "display_name": "Alaa",
        "phone_number": None,
        "relation_type": RelationType.CORE_FAMILY.value,
        "specific_relation": "Schwester",
        "preferred_lang": "Arabisch (syrischer Dialekt)",
        "auto_mode": AutoMode.AUTO_LOW_RISK.value,
        "categories": ["CORE_FAMILY"],
        "style": "Liebevoll, spielerisch, leicht frech, aber nie respektlos. Bei ernsten Themen sofort ernst.",
    },
    {
        "display_name": "Namat",
        "phone_number": None,
        "relation_type": RelationType.CORE_FAMILY.value,
        "specific_relation": "Schwester",
        "preferred_lang": "Arabisch (syrischer Dialekt)",
        "auto_mode": AutoMode.AUTO_LOW_RISK.value,
        "categories": ["CORE_FAMILY"],
        "style": "Liebevoll, spielerisch und beschuetzend. Ihre Tochter Maha merken.",
    },
    {
        "display_name": "Ataa",
        "phone_number": None,
        "relation_type": RelationType.EXTENDED_FAMILY.value,
        "specific_relation": "Onkel",
        "preferred_lang": "Arabisch (syrischer Dialekt)",
        "auto_mode": AutoMode.AUTO_LOW_RISK.value,
        "categories": ["EXTENDED_FAMILY"],
        "style": "Respektvoll, ruhig, knapp und herzlich. Anrede: eami abu malik.",
    },
    {
        "display_name": "Ahmad",
        "phone_number": None,
        "relation_type": RelationType.EXTENDED_FAMILY.value,
        "specific_relation": "Onkel",
        "preferred_lang": "Arabisch (syrischer Dialekt)",
        "auto_mode": AutoMode.AUTO_LOW_RISK.value,
        "categories": ["EXTENDED_FAMILY"],
        "style": "Respektvoll, ruhig, knapp und herzlich. Anrede: eami abu anas.",
    },
    {
        "display_name": "abu Ayham",
        "phone_number": None,
        "relation_type": RelationType.EXTENDED_FAMILY.value,
        "specific_relation": "Onkel",
        "preferred_lang": "Arabisch (syrischer Dialekt)",
        "auto_mode": AutoMode.AUTO_LOW_RISK.value,
        "categories": ["EXTENDED_FAMILY"],
        "style": "Respektvoll, ruhig, knapp und herzlich. Anrede: eami abu ayham.",
    },
    {
        "display_name": "abu walid",
        "phone_number": None,
        "relation_type": RelationType.EXTENDED_FAMILY.value,
        "specific_relation": "Grossvater",
        "preferred_lang": "Arabisch (syrischer Dialekt)",
        "auto_mode": AutoMode.AUTO_LOW_RISK.value,
        "categories": ["EXTENDED_FAMILY"],
        "style": "Sehr respektvoll, ruhig, knapp und herzlich. Anrede: jdi abu Walid.",
    },
    {
        "display_name": "Sti Umm Walid",
        "phone_number": None,
        "relation_type": RelationType.EXTENDED_FAMILY.value,
        "specific_relation": "Grossmutter",
        "preferred_lang": "Arabisch (syrischer Dialekt)",
        "auto_mode": AutoMode.AUTO_LOW_RISK.value,
        "categories": ["EXTENDED_FAMILY"],
        "style": "Sehr respektvoll, warm und herzlich. Anrede: Sti Umm Walid.",
    },
]


NOTE_SEEDS = [
    {
        "scope": NoteScope.GLOBAL.value,
        "content": "Bei unklaren oder sensiblen Themen lieber kurz nachfragen statt raten.",
        "pinned": True,
        "priority": 100,
    },
    {
        "scope": NoteScope.CATEGORY.value,
        "category": "WORK",
        "content": "Arbeitsanfragen immer in Review halten. Keine automatische Zusage zu Schichten.",
        "pinned": True,
        "priority": 100,
    },
    {
        "scope": NoteScope.CATEGORY.value,
        "category": "EXTENDED_FAMILY",
        "content": "Bei Feiertagen respektvoll, herzlich und mit kurzer persoenlicher Anrede gratulieren.",
        "pinned": True,
        "priority": 50,
    },
]


EVENT_TICKET_SEEDS = [
    {
        "title": "Feiertagsgruss an Verwandte",
        "target_type": EventTargetType.CATEGORY.value,
        "target_category": "EXTENDED_FAMILY",
        "base_text": "Ich wuensche dir und deiner Familie einen gesegneten Feiertag. Liebe Gruesse!",
    }
]


def get_or_create_category(db, name: str) -> ContactCategory:
    normalized = name.strip().upper()
    category = db.query(ContactCategory).filter(ContactCategory.name == normalized).first()
    if category:
        return category
    category = ContactCategory(name=normalized, label=normalized.title())
    db.add(category)
    db.flush()
    return category


def seed_contacts(db, seeds: Iterable[dict]):
    for data in seeds:
        contact = db.query(Contact).filter(Contact.display_name == data["display_name"]).first()
        if not contact:
            contact = Contact(
                display_name=data["display_name"],
                phone_number=data.get("phone_number"),
                relation_type=data.get("relation_type", RelationType.UNKNOWN.value),
                specific_relation=data.get("specific_relation"),
                preferred_lang=data.get("preferred_lang", "Deutsch"),
                auto_mode=data.get("auto_mode", AutoMode.REVIEW.value),
                is_active=True,
            )
            db.add(contact)
            db.flush()

        if data.get("phone_number") and not contact.phone_number:
            contact.phone_number = data["phone_number"]
        if not contact.categories:
            contact.categories = [get_or_create_category(db, c) for c in data.get("categories", [])]
        if not contact.rules:
            contact.rules = ContactRule(contact_id=contact.id)
        if not contact.rules.style:
            contact.rules.style = data.get("style")
        if not contact.rules.allowed_topics:
            contact.rules.allowed_topics = data.get("allowed_topics")
        if not contact.rules.blocked_topics:
            contact.rules.blocked_topics = data.get("blocked_topics")


def seed_notes(db, seeds: Iterable[dict]):
    for data in seeds:
        existing = (
            db.query(Note)
            .filter(Note.scope == data["scope"], Note.category == data.get("category"), Note.content == data["content"])
            .first()
        )
        if existing:
            existing.pinned = data.get("pinned", False)
            existing.priority = data.get("priority", 0)
            continue
        db.add(
            Note(
                scope=data["scope"],
                category=data.get("category"),
                content=data["content"],
                pinned=data.get("pinned", False),
                priority=data.get("priority", 0),
            )
        )


def seed_event_tickets(db, seeds: Iterable[dict]):
    for data in seeds:
        existing = db.query(EventTicket).filter(EventTicket.title == data["title"]).first()
        if existing:
            existing.target_type = data["target_type"]
            existing.target_category = data.get("target_category")
            existing.base_text = data.get("base_text")
            continue
        db.add(
            EventTicket(
                title=data["title"],
                target_type=data["target_type"],
                target_category=data.get("target_category"),
                base_text=data.get("base_text"),
                prompt=data.get("prompt"),
                scheduled_at=__import__("datetime").datetime.utcnow(),
            )
        )


def seed_database():
    db = SessionLocal()
    try:
        seed_contacts(db, CONTACT_SEEDS)
        seed_notes(db, NOTE_SEEDS)
        seed_event_tickets(db, EVENT_TICKET_SEEDS)
        db.commit()
    except Exception:
        db.rollback()
        raise
    finally:
        db.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Seed or reset the WhatsApp Agent v3 database.")
    parser.add_argument(
        "--reset",
        action="store_true",
        help="Drop and recreate the database schema before seeding. Omit for the normal one-time idempotent seed.",
    )
    args = parser.parse_args()

    init_db(reset=args.reset)
    seed_database()
    print("Database seeded." if not args.reset else "Database reset and seeded.")
