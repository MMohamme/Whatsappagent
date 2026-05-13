import datetime
import enum
import os
from pathlib import Path

from sqlalchemy import (
    Boolean,
    Column,
    DateTime,
    ForeignKey,
    Integer,
    String,
    Table,
    Text,
    create_engine,
)
from sqlalchemy.orm import declarative_base, relationship, sessionmaker


Base = declarative_base()


class RelationType(str, enum.Enum):
    CORE_FAMILY = "CORE_FAMILY"
    EXTENDED_FAMILY = "EXTENDED_FAMILY"
    FRIEND = "FRIEND"
    WORK = "WORK"
    UNKNOWN = "UNKNOWN"
    CUSTOM = "CUSTOM"


class AutoMode(str, enum.Enum):
    OFF = "OFF"
    REVIEW = "REVIEW"
    AUTO_LOW_RISK = "AUTO_LOW_RISK"
    AUTO_TRUSTED = "AUTO_TRUSTED"


class AgentDecision(str, enum.Enum):
    IGNORE = "IGNORE"
    DRAFT_ONLY = "DRAFT_ONLY"
    NEEDS_REVIEW = "NEEDS_REVIEW"
    AUTO_SEND_ALLOWED = "AUTO_SEND_ALLOWED"
    BLOCKED = "BLOCKED"


class RiskLevel(str, enum.Enum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"


class MessageStatus(str, enum.Enum):
    RECEIVED = "RECEIVED"
    DEDUPED = "DEDUPED"
    CLASSIFIED = "CLASSIFIED"
    DRAFTED = "DRAFTED"
    NEEDS_REVIEW = "NEEDS_REVIEW"
    SEND_PENDING = "SEND_PENDING"
    SENDING = "SENDING"
    SENT = "SENT"
    FAILED = "FAILED"
    SKIPPED = "SKIPPED"
    BLOCKED = "BLOCKED"


class SendChannel(str, enum.Enum):
    ANDROID_REMOTE_INPUT = "ANDROID_REMOTE_INPUT"
    ANDROID_ACCESSIBILITY = "ANDROID_ACCESSIBILITY"


class NoteScope(str, enum.Enum):
    GLOBAL = "GLOBAL"
    CATEGORY = "CATEGORY"
    CONTACT = "CONTACT"


class EventTargetType(str, enum.Enum):
    CONTACT = "CONTACT"
    CATEGORY = "CATEGORY"
    GLOBAL = "GLOBAL"


class EventTicketStatus(str, enum.Enum):
    DRAFT = "DRAFT"
    PREPARED = "PREPARED"
    APPROVED = "APPROVED"
    SENDING = "SENDING"
    PARTIAL_FAILED = "PARTIAL_FAILED"
    SENT = "SENT"
    CANCELLED = "CANCELLED"


contact_category_links = Table(
    "contact_category_links",
    Base.metadata,
    Column("contact_id", Integer, ForeignKey("contacts.id"), primary_key=True),
    Column("category_id", Integer, ForeignKey("contact_categories.id"), primary_key=True),
)


class ContactCategory(Base):
    __tablename__ = "contact_categories"

    id = Column(Integer, primary_key=True, autoincrement=True)
    name = Column(String, unique=True, nullable=False, index=True)
    label = Column(String)

    contacts = relationship("Contact", secondary=contact_category_links, back_populates="categories")


class Contact(Base):
    __tablename__ = "contacts"

    id = Column(Integer, primary_key=True, autoincrement=True)
    display_name = Column(String, nullable=False, index=True)
    phone_number = Column(String, unique=True, nullable=True, index=True)
    relation_type = Column(String, default=RelationType.UNKNOWN.value, nullable=False)
    specific_relation = Column(String)
    preferred_lang = Column(String, default="Deutsch")
    is_active = Column(Boolean, default=True, nullable=False)
    auto_mode = Column(String, default=AutoMode.REVIEW.value, nullable=False)
    created_at = Column(DateTime, default=datetime.datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.datetime.utcnow, onupdate=datetime.datetime.utcnow)

    categories = relationship("ContactCategory", secondary=contact_category_links, back_populates="contacts")
    rules = relationship("ContactRule", back_populates="contact", uselist=False, cascade="all, delete-orphan")
    messages = relationship("Message", back_populates="contact", cascade="all, delete-orphan")
    notes = relationship("Note", back_populates="contact", cascade="all, delete-orphan")


class ContactRule(Base):
    __tablename__ = "contact_rules"

    id = Column(Integer, primary_key=True, autoincrement=True)
    contact_id = Column(Integer, ForeignKey("contacts.id"), unique=True, nullable=False)
    style = Column(Text)
    allowed_topics = Column(Text)
    blocked_topics = Column(Text)
    delay_min_ms = Column(Integer, default=5000)
    delay_max_ms = Column(Integer, default=25000)
    updated_at = Column(DateTime, default=datetime.datetime.utcnow, onupdate=datetime.datetime.utcnow)

    contact = relationship("Contact", back_populates="rules")


class Message(Base):
    __tablename__ = "messages"

    id = Column(Integer, primary_key=True, autoincrement=True)
    custom_id = Column(String, unique=True, nullable=False, index=True)
    contact_id = Column(Integer, ForeignKey("contacts.id"), nullable=False, index=True)
    channel_message_id = Column(String, index=True)
    package_name = Column(String)
    notification_key = Column(String)
    role = Column(String, nullable=False)
    content = Column(Text, nullable=False)
    status = Column(String, default=MessageStatus.RECEIVED.value, nullable=False, index=True)
    category = Column(String)
    timestamp = Column(DateTime, default=datetime.datetime.utcnow, index=True)

    contact = relationship("Contact", back_populates="messages")
    drafts = relationship("Draft", back_populates="message", cascade="all, delete-orphan")


class Draft(Base):
    __tablename__ = "drafts"

    id = Column(Integer, primary_key=True, autoincrement=True)
    message_id = Column(Integer, ForeignKey("messages.id"), nullable=True, index=True)
    event_recipient_id = Column(Integer, ForeignKey("event_recipients.id"), nullable=True, index=True)
    reply_text = Column(Text, nullable=False)
    decision = Column(String, default=AgentDecision.NEEDS_REVIEW.value, nullable=False, index=True)
    risk_level = Column(String, default=RiskLevel.MEDIUM.value, nullable=False)
    reason = Column(Text)
    category = Column(String)
    recommended_delay_ms = Column(Integer, default=0)
    created_at = Column(DateTime, default=datetime.datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.datetime.utcnow, onupdate=datetime.datetime.utcnow)

    message = relationship("Message", back_populates="drafts")
    send_attempts = relationship("SendAttempt", back_populates="draft", cascade="all, delete-orphan")


class SendAttempt(Base):
    __tablename__ = "send_attempts"

    id = Column(Integer, primary_key=True, autoincrement=True)
    draft_id = Column(Integer, ForeignKey("drafts.id"), nullable=False, index=True)
    channel = Column(String, default=SendChannel.ANDROID_REMOTE_INPUT.value, nullable=False)
    status = Column(String, default=MessageStatus.SEND_PENDING.value, nullable=False, index=True)
    error = Column(Text)
    attempt_count = Column(Integer, default=0)
    created_at = Column(DateTime, default=datetime.datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.datetime.utcnow, onupdate=datetime.datetime.utcnow)
    sent_at = Column(DateTime)

    draft = relationship("Draft", back_populates="send_attempts")


class Note(Base):
    __tablename__ = "notes"

    id = Column(Integer, primary_key=True, autoincrement=True)
    scope = Column(String, default=NoteScope.CONTACT.value, nullable=False, index=True)
    contact_id = Column(Integer, ForeignKey("contacts.id"), nullable=True, index=True)
    category = Column(String, nullable=True, index=True)
    content = Column(Text, nullable=False)
    pinned = Column(Boolean, default=False, nullable=False)
    priority = Column(Integer, default=0, nullable=False)
    valid_from = Column(DateTime, nullable=True)
    expires_at = Column(DateTime, nullable=True)
    created_at = Column(DateTime, default=datetime.datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.datetime.utcnow, onupdate=datetime.datetime.utcnow)

    contact = relationship("Contact", back_populates="notes")


class EventTicket(Base):
    __tablename__ = "event_tickets"

    id = Column(Integer, primary_key=True, autoincrement=True)
    title = Column(String, nullable=False)
    target_type = Column(String, nullable=False)
    target_contact_id = Column(Integer, ForeignKey("contacts.id"), nullable=True)
    target_category = Column(String, nullable=True, index=True)
    scheduled_at = Column(DateTime, nullable=False, index=True)
    base_text = Column(Text)
    prompt = Column(Text)
    status = Column(String, default=EventTicketStatus.DRAFT.value, nullable=False, index=True)
    stagger_min_seconds = Column(Integer, default=20)
    stagger_max_seconds = Column(Integer, default=90)
    created_at = Column(DateTime, default=datetime.datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.datetime.utcnow, onupdate=datetime.datetime.utcnow)

    recipients = relationship("EventRecipient", back_populates="ticket", cascade="all, delete-orphan")


class EventRecipient(Base):
    __tablename__ = "event_recipients"

    id = Column(Integer, primary_key=True, autoincrement=True)
    ticket_id = Column(Integer, ForeignKey("event_tickets.id"), nullable=False, index=True)
    contact_id = Column(Integer, ForeignKey("contacts.id"), nullable=False, index=True)
    draft_id = Column(Integer, ForeignKey("drafts.id"), nullable=True)
    status = Column(String, default=MessageStatus.NEEDS_REVIEW.value, nullable=False, index=True)
    error = Column(Text)
    scheduled_at = Column(DateTime, nullable=False, index=True)
    created_at = Column(DateTime, default=datetime.datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.datetime.utcnow, onupdate=datetime.datetime.utcnow)

    ticket = relationship("EventTicket", back_populates="recipients")
    contact = relationship("Contact")
    draft = relationship("Draft", foreign_keys=[draft_id], post_update=True)


class Event(Base):
    __tablename__ = "events"

    id = Column(Integer, primary_key=True, autoincrement=True)
    event_recipient_id = Column(Integer, ForeignKey("event_recipients.id"), nullable=True, index=True)
    contact_id = Column(Integer, ForeignKey("contacts.id"), nullable=False, index=True)
    scheduled_at = Column(DateTime, nullable=False, index=True)
    status = Column(String, default=MessageStatus.SEND_PENDING.value, nullable=False, index=True)
    generated_text = Column(Text)
    created_at = Column(DateTime, default=datetime.datetime.utcnow)


class AuditLog(Base):
    __tablename__ = "audit_logs"

    id = Column(Integer, primary_key=True, autoincrement=True)
    action = Column(String, nullable=False, index=True)
    entity_type = Column(String)
    entity_id = Column(String)
    detail = Column(Text)
    created_at = Column(DateTime, default=datetime.datetime.utcnow)


PROJECT_ROOT = Path(__file__).resolve().parent.parent
DATABASE_URL = os.getenv("DATABASE_URL", f"sqlite:///{(PROJECT_ROOT / 'whatsapp_agent.db').as_posix()}")
engine = create_engine(DATABASE_URL, connect_args={"check_same_thread": False})
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def init_db(reset: bool | None = None):
    if reset is None:
        reset = os.getenv("RESET_DB_ON_START", "0") == "1"
    if reset:
        Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)


if __name__ == "__main__":
    init_db(reset=True)
    print("Database reset and initialized.")
