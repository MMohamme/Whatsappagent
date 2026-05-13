from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from backend import database as dbmod
from backend import migrate
from backend.database import AutoMode, Base, Contact, ContactRule, RelationType


def temp_session_factory(tmp_path):
    engine = create_engine(
        f"sqlite:///{tmp_path / 'test.db'}",
        connect_args={"check_same_thread": False},
    )
    Base.metadata.create_all(bind=engine)
    return sessionmaker(autocommit=False, autoflush=False, bind=engine)


def test_init_db_does_not_reset_by_default(monkeypatch, tmp_path):
    engine = create_engine(
        f"sqlite:///{tmp_path / 'startup.db'}",
        connect_args={"check_same_thread": False},
    )
    monkeypatch.setattr(dbmod, "engine", engine)
    dbmod.SessionLocal.configure(bind=engine)
    monkeypatch.delenv("RESET_DB_ON_START", raising=False)

    dbmod.init_db(reset=True)
    db = dbmod.SessionLocal()
    try:
        db.add(Contact(display_name="Keep Me", relation_type=RelationType.FRIEND.value))
        db.commit()
    finally:
        db.close()

    dbmod.init_db()

    db = dbmod.SessionLocal()
    try:
        assert db.query(Contact).filter(Contact.display_name == "Keep Me").count() == 1
    finally:
        db.close()


def test_seed_database_is_idempotent_and_preserves_phone_metadata(monkeypatch, tmp_path):
    SessionLocal = temp_session_factory(tmp_path)
    monkeypatch.setattr(migrate, "SessionLocal", SessionLocal)

    migrate.seed_database()
    migrate.seed_database()

    db = SessionLocal()
    try:
        mazen = db.query(Contact).filter(Contact.display_name == "Mazen").one()
        mazen.phone_number = "491700000000"
        mazen.auto_mode = AutoMode.AUTO_TRUSTED.value
        db.commit()
    finally:
        db.close()

    migrate.seed_database()

    db = SessionLocal()
    try:
        mazen = db.query(Contact).filter(Contact.display_name == "Mazen").one()
        assert mazen.phone_number == "491700000000"
        assert mazen.auto_mode == AutoMode.AUTO_TRUSTED.value
        assert db.query(Contact).filter(Contact.display_name == "Mazen").count() == 1
    finally:
        db.close()


def test_apply_contact_payload_patches_phone_without_losing_rules(tmp_path):
    from backend.main import ContactUpdate, apply_contact_payload

    SessionLocal = temp_session_factory(tmp_path)
    db = SessionLocal()
    try:
        contact = Contact(
            display_name="Alaa",
            relation_type=RelationType.CORE_FAMILY.value,
            specific_relation="Schwester",
            preferred_lang="Deutsch",
            auto_mode=AutoMode.AUTO_LOW_RISK.value,
            is_active=True,
        )
        contact.rules = ContactRule(style="Warm")
        db.add(contact)
        db.flush()

        apply_contact_payload(db, contact, ContactUpdate(phone_number="491711111111"))
        db.commit()

        assert contact.phone_number == "491711111111"
        assert contact.display_name == "Alaa"
        assert contact.rules.style == "Warm"
        assert contact.auto_mode == AutoMode.AUTO_LOW_RISK.value
    finally:
        db.close()
