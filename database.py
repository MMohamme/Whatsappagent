from sqlalchemy import create_engine, Column, String, Integer, DateTime, ForeignKey, Boolean, Enum, Text
from sqlalchemy.orm import declarative_base
from sqlalchemy.orm import sessionmaker, relationship
import datetime
import enum

Base = declarative_base()

# =====================
# ENUMS
# =====================

class RelationType(enum.Enum):
    CORE_FAMILY = "core_family"
    EXTENDED_FAMILY = "extended_family"
    FRIEND = "friend"
    WORK = "work"
    UNKNOWN = "unknown"

class EventStatus(enum.Enum):
    PENDING = "PENDING"
    TRIGGERED = "TRIGGERED"
    SENT = "SENT"
    FAILED = "FAILED"
    CANCELLED = "CANCELLED"

class EventType(enum.Enum):
    GREETING = "GREETING"
    REMINDER = "REMINDER"
    PROACTIVE = "PROACTIVE"
    CUSTOM = "CUSTOM"

# =====================
# MODELS
# =====================

class Contact(Base):
    __tablename__ = "contacts"

    contact_name = Column(String, primary_key=True)
    relation_type = Column(Enum(RelationType), default=RelationType.UNKNOWN)
    specific_relation = Column(String)
    preferred_lang = Column(String, default="Deutsch")
    behavior_rules = Column(Text)
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime, default=datetime.datetime.utcnow)

    messages = relationship("Message", back_populates="contact", cascade="all, delete-orphan")
    notes = relationship("Note", back_populates="contact", cascade="all, delete-orphan")
    events = relationship("Event", back_populates="contact", cascade="all, delete-orphan")


class Message(Base):
    __tablename__ = "messages"

    msg_id = Column(String, primary_key=True)
    contact_name = Column(String, ForeignKey("contacts.contact_name"))
    role = Column(String)
    content = Column(Text, nullable=False)
    timestamp = Column(DateTime, default=datetime.datetime.utcnow)
    category = Column(String)
    is_transcribed = Column(Boolean, default=False)
    status = Column(String, default="CAPTURED")

    contact = relationship("Contact", back_populates="messages")


class Note(Base):
    __tablename__ = "notes"

    id = Column(Integer, primary_key=True, autoincrement=True)
    contact_name = Column(String, ForeignKey("contacts.contact_name"), nullable=False)
    content = Column(Text, nullable=False)
    created_at = Column(DateTime, default=datetime.datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.datetime.utcnow, onupdate=datetime.datetime.utcnow)
    pinned = Column(Boolean, default=False)
    expiry_date = Column(DateTime, nullable=True) # NEU: Ablaufdatum

    contact = relationship("Contact", back_populates="notes")


class Event(Base):
    __tablename__ = "events"

    id = Column(Integer, primary_key=True, autoincrement=True)
    contact_name = Column(String, ForeignKey("contacts.contact_name"), nullable=False)
    event_type = Column(Enum(EventType), default=EventType.CUSTOM)
    title = Column(String, nullable=False)
    description = Column(Text)
    status = Column(Enum(EventStatus), default=EventStatus.PENDING)
    scheduled_at = Column(DateTime, nullable=False)
    generated_text = Column(Text)
    sent_at = Column(DateTime)
    created_at = Column(DateTime, default=datetime.datetime.utcnow)

    contact = relationship("Contact", back_populates="events")


class Task(Base):
    __tablename__ = "tasks"

    id = Column(Integer, primary_key=True, autoincrement=True)
    contact_name = Column(String, ForeignKey("contacts.contact_name"))
    task_type = Column(String)
    status = Column(String, default="PENDING")
    scheduled_at = Column(DateTime)
    generated_text = Column(Text)


# =====================
# DB INIT
# =====================

DATABASE_URL = "sqlite:///./whatsapp_agent.db"
engine = create_engine(DATABASE_URL, connect_args={"check_same_thread": False})
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

def init_db():
    Base.metadata.create_all(bind=engine)

if __name__ == "__main__":
    init_db()
    print("Datenbank initialisiert.")