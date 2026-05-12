from fastapi import FastAPI, Depends, HTTPException, Query
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from google import genai
from dotenv import load_dotenv
from typing import List, Dict, Optional
import os
import json
import time
import traceback
import datetime
from sqlalchemy.orm import Session
from sqlalchemy import func

from database import (
    SessionLocal, init_db,
    Contact, Message, RelationType, Task,
    Note, Event, EventStatus, EventType
)

load_dotenv()
init_db()

app = FastAPI(title="WA Agent Backend", version="2.0.0")
client = genai.Client(api_key=os.getenv("GEMINI_API_KEY"))

# =========================
# RATE LIMIT
# =========================
RATE_LIMIT_SECONDS = 30
_last_request_time: Dict[str, float] = {}

def check_rate_limit(sender: str) -> bool:
    now = time.time()
    last = _last_request_time.get(sender, 0.0)
    if now - last < RATE_LIMIT_SECONDS:
        return False
    _last_request_time[sender] = now
    return True

# =========================
# DB DEPENDENCY
# =========================
def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

# =========================
# PYDANTIC MODELS
# =========================

class MessageSchema(BaseModel):
    custom_id: str
    sender: str
    text: str
    history: List[Dict[str, str]] = []

class ContactResponse(BaseModel):
    contact_name: str
    relation_type: str
    specific_relation: Optional[str] = None
    preferred_lang: Optional[str] = None
    behavior_rules: Optional[str] = None
    replies_count: int = 0
    last_seen: Optional[str] = None
    active: bool = True

class ContactCreate(BaseModel):
    contact_name: str
    relation_type: str
    specific_relation: Optional[str] = None
    preferred_lang: Optional[str] = None
    behavior_rules: Optional[str] = None

class ContactUpdate(BaseModel):
    specific_relation: Optional[str] = None
    preferred_lang: Optional[str] = None
    behavior_rules: Optional[str] = None
    active: Optional[bool] = None

# --- Notes ---
class NoteCreate(BaseModel):
    content: str
    pinned: bool = False
    expiry_date: Optional[str] = None # ISO 8601

class NoteUpdate(BaseModel):
    content: Optional[str] = None
    pinned: Optional[bool] = None
    expiry_date: Optional[str] = None

class NoteResponse(BaseModel):
    id: int
    contact_name: str
    content: str
    pinned: bool
    expiry_date: Optional[str] = None
    created_at: Optional[str] = None
    updated_at: Optional[str] = None

# --- Events ---
class EventCreate(BaseModel):
    title: str
    event_type: str = "CUSTOM"
    description: Optional[str] = None
    scheduled_at: str  # ISO 8601 string
    generated_text: Optional[str] = None

class EventUpdate(BaseModel):
    title: Optional[str] = None
    description: Optional[str] = None
    scheduled_at: Optional[str] = None
    generated_text: Optional[str] = None
    status: Optional[str] = None

class EventResponse(BaseModel):
    id: int
    contact_name: str
    event_type: str
    title: str
    description: Optional[str] = None
    status: str
    scheduled_at: Optional[str] = None
    generated_text: Optional[str] = None
    sent_at: Optional[str] = None
    created_at: Optional[str] = None

# =========================
# PERSONA PROMPT
# =========================
def get_persona_prompt(contact: Contact) -> str:
    relation = contact.specific_relation or "Bekannte Person"
    lang = contact.preferred_lang or "Deutsch"
    rules = contact.behavior_rules or "Antworte freundlich und natürlich."
    return f"""Du bist Bassam (Mo), 27, Data Science Student, arbeitet als Koch.
Du schreibst mit {contact.contact_name} ({relation}). Sprache: {lang}.

Regeln:
{rules}

Allgemeine Regeln:
- Halte dich an 1-3 Sätze.
- Wenn jemand dich fragt arbeiten zu kommen, antworte IMMER mit einem klaren Nein (außer es gibt eine explizite Ja-Anweisung). Gib einen kreativen Grund an, aber KEIN Krankheitsgrund.
- Wenn du eine Frage nicht verstehst, frage nach einer Klarstellung, anstatt zu raten.

WICHTIG: Antworte IMMER in exakt diesem JSON-Format (kein Markdown, keine Backticks drumherum):
{{"reply": "Deine menschliche Antwort", "category": "Das Hauptthema (z.B. Arbeit, Familie, Termin)"}}"""

# =========================
# /generate
# =========================
@app.post("/generate")
def generate(msg: MessageSchema, db: Session = Depends(get_db)):
    try:
        if not check_rate_limit(msg.sender):
            remaining = int(RATE_LIMIT_SECONDS - (time.time() - _last_request_time.get(msg.sender, 0)))
            return JSONResponse(
                status_code=429,
                content={"error": f"Rate limit: noch {remaining}s warten für {msg.sender}"}
            )

        contact = db.query(Contact).filter(Contact.contact_name == msg.sender).first()
        if not contact:
            contact = Contact(contact_name=msg.sender, relation_type=RelationType.UNKNOWN)
            db.add(contact)
            db.commit()
            db.refresh(contact)

        # Deduplizierung
        existing = db.query(Message).filter(Message.msg_id == msg.custom_id).first()
        if existing:
            return {"reply": "Bereits verarbeitet"}

        # Eingehende Nachricht speichern
        new_msg = Message(
            msg_id=msg.custom_id,
            contact_name=msg.sender,
            role="user",
            content=msg.text,
            status="CAPTURED"
        )
        db.add(new_msg)
        db.commit()

        # History aus DB
        db_history = (
            db.query(Message)
            .filter(
                Message.contact_name == msg.sender,
                Message.msg_id != msg.custom_id
            )
            .order_by(Message.timestamp.desc())
            .limit(12)
            .all()
        )
        db_history = list(reversed(db_history))

        history_text = ""
        if db_history:
            history_text = "\nBisheriger Gesprächsverlauf:\n"
            for h in db_history:
                role_name = "Du" if h.role == "assistant" else contact.contact_name
                history_text += f"{role_name}: {h.content}\n"
        elif msg.history:
            history_text = "\nBisheriger Gesprächsverlauf:\n"
            for h in msg.history[-12:]:
                role_name = "Du" if h.get("role") == "assistant" else contact.contact_name
                history_text += f"{role_name}: {h.get('text', '')}\n"

        # Notizen des Kontakts als Kontext einfügen (nur nicht abgelaufene)
        now = datetime.datetime.utcnow()
        notes = (
            db.query(Note)
            .filter(
                Note.contact_name == msg.sender,
                (Note.expiry_date == None) | (Note.expiry_date > now)
            )
            .order_by(Note.pinned.desc())
            .all()
        )
        notes_text = ""
        if notes:
            notes_text = "\nKontext-Notizen zu diesem Kontakt:\n"
            for note in notes:
                notes_text += f"- {note.content}\n"

        system_instruction = get_persona_prompt(contact)
        full_prompt = f"{notes_text}{history_text}\nNeue Nachricht von {contact.contact_name}: {msg.text}\nAntworte jetzt im JSON-Format:"

        response = client.models.generate_content(
            model="gemini-2.5-flash-lite",
            config={"system_instruction": system_instruction},
            contents=full_prompt
        )

        raw_text = response.text.replace("```json", "").replace("```", "").strip()
        try:
            data = json.loads(raw_text)
            reply_text = data.get("reply", "").strip()
            category = data.get("category", "Allgemein")
        except json.JSONDecodeError:
            reply_text = raw_text
            category = "Unbekannt"

        if not reply_text:
            return JSONResponse(status_code=500, content={"error": "Gemini hat eine leere Antwort zurückgegeben."})

        # Status-Update + Antwort speichern
        new_msg.status = "REPLY_PENDING"
        reply_msg = Message(
            msg_id=f"reply_{msg.custom_id}",
            contact_name=msg.sender,
            role="assistant",
            content=reply_text,
            category=category,
            status="REPLY_SENT"
        )
        db.add(reply_msg)
        db.commit()

        return {"reply": reply_text}

    except Exception as e:
        db.rollback()
        traceback.print_exc()
        return JSONResponse(status_code=500, content={"error": str(e)})


@app.patch("/messages/{msg_id}")
def update_message_status(msg_id: str, status: str = Query(...), db: Session = Depends(get_db)):
    """Update message status (e.g., mark as REPLY_SENT)."""
    message = db.query(Message).filter(Message.msg_id == msg_id).first()
    if not message:
        # Check for user message if reply_msg_id was sent
        message = db.query(Message).filter(Message.msg_id == msg_id.replace("reply_", "")).first()

    if not message:
        raise HTTPException(status_code=404, detail="Message not found")

    message.status = status
    db.commit()
    return {"status": "ok", "msg_id": msg_id, "new_status": status}


# =========================
# DASHBOARD / STATS
# =========================
@app.get("/stats")
def get_stats(db: Session = Depends(get_db)):
    """
    Aggregierte Statistiken für das Dashboard.
    Entspricht dem, was DashboardViewModel.kt braucht.
    """
    total_contacts = db.query(Contact).count()
    active_contacts = db.query(Contact).filter(Contact.is_active == True).count()
    total_messages = db.query(Message).count()
    total_replies = db.query(Message).filter(Message.role == "assistant").count()
    pending_replies = db.query(Message).filter(Message.status == "REPLY_PENDING").count()
    failed_replies = db.query(Message).filter(Message.status == "REPLY_FAILED").count()

    # Nachrichten der letzten 7 Tage (pro Tag)
    seven_days_ago = datetime.datetime.utcnow() - datetime.timedelta(days=7)
    daily_messages = (
        db.query(
            func.date(Message.timestamp).label("day"),
            func.count(Message.msg_id).label("count")
        )
        .filter(Message.timestamp >= seven_days_ago)
        .group_by(func.date(Message.timestamp))
        .order_by(func.date(Message.timestamp))
        .all()
    )

    # Kategorien-Verteilung (Top 5)
    top_categories = (
        db.query(Message.category, func.count(Message.msg_id).label("count"))
        .filter(Message.category != None)
        .group_by(Message.category)
        .order_by(func.count(Message.msg_id).desc())
        .limit(5)
        .all()
    )

    # Pending Events
    pending_events = db.query(Event).filter(Event.status == EventStatus.PENDING).count()

    return {
        "contacts": {
            "total": total_contacts,
            "active": active_contacts,
        },
        "messages": {
            "total": total_messages,
            "total_replies": total_replies,
            "pending_replies": pending_replies,
            "failed_replies": failed_replies,
        },
        "events": {
            "pending": pending_events,
        },
        "chart_daily": [
            {"day": str(row.day), "count": row.count}
            for row in daily_messages
        ],
        "chart_categories": [
            {"category": row.category or "Unbekannt", "count": row.count}
            for row in top_categories
        ],
    }


# =========================
# QUEUE ENDPOINT
# =========================
@app.get("/queue")
def get_queue(
    status: Optional[str] = Query(None, description="Filter: CAPTURED, REPLY_PENDING, REPLY_SENT, REPLY_FAILED"),
    limit: int = Query(50, ge=1, le=200),
    db: Session = Depends(get_db)
):
    """
    Nachrichten-Queue für QueueViewModel.kt.
    Standardmäßig nur user-Nachrichten (eingehend), optional nach Status filterbar.
    """
    query = db.query(Message).filter(Message.role == "user")
    if status:
        query = query.filter(Message.status == status)
    messages = query.order_by(Message.timestamp.desc()).limit(limit).all()

    return [
        {
            "msg_id": msg.msg_id,
            "contact_name": msg.contact_name,
            "content": msg.content,
            "timestamp": msg.timestamp.isoformat() if msg.timestamp else None,
            "status": msg.status,
            "category": msg.category,
        }
        for msg in messages
    ]


# =========================
# CONTACTS ENDPOINTS
# =========================

def _contact_to_response(contact: Contact, db: Session, active_override: Optional[bool] = None) -> ContactResponse:
    replies_count = db.query(Message).filter(
        Message.contact_name == contact.contact_name,
        Message.role == "assistant"
    ).count()
    last_msg = db.query(Message).filter(
        Message.contact_name == contact.contact_name
    ).order_by(Message.timestamp.desc()).first()
    last_seen = last_msg.timestamp.isoformat() if (last_msg and last_msg.timestamp) else None

    return ContactResponse(
        contact_name=contact.contact_name,
        relation_type=contact.relation_type.value,
        specific_relation=contact.specific_relation,
        preferred_lang=contact.preferred_lang,
        behavior_rules=contact.behavior_rules,
        replies_count=replies_count,
        last_seen=last_seen,
        active=active_override if active_override is not None else contact.is_active,
    )


@app.get("/contacts", response_model=List[ContactResponse])
def get_contacts(db: Session = Depends(get_db)):
    contacts = db.query(Contact).all()
    return [_contact_to_response(c, db) for c in contacts]


@app.post("/contacts", response_model=ContactResponse)
def create_contact(contact: ContactCreate, db: Session = Depends(get_db)):
    existing = db.query(Contact).filter(Contact.contact_name == contact.contact_name).first()
    if existing:
        raise HTTPException(status_code=400, detail="Contact already exists")
    new_contact = Contact(
        contact_name=contact.contact_name,
        relation_type=RelationType(contact.relation_type),
        specific_relation=contact.specific_relation,
        preferred_lang=contact.preferred_lang,
        behavior_rules=contact.behavior_rules,
        is_active=True,
    )
    db.add(new_contact)
    db.commit()
    db.refresh(new_contact)
    return _contact_to_response(new_contact, db)


@app.patch("/contacts/{contact_name}", response_model=ContactResponse)
def update_contact(contact_name: str, update: ContactUpdate, db: Session = Depends(get_db)):
    contact = db.query(Contact).filter(Contact.contact_name == contact_name).first()
    if not contact:
        raise HTTPException(status_code=404, detail="Contact not found")
    if update.specific_relation is not None:
        contact.specific_relation = update.specific_relation
    if update.preferred_lang is not None:
        contact.preferred_lang = update.preferred_lang
    if update.behavior_rules is not None:
        contact.behavior_rules = update.behavior_rules
    if update.active is not None:
        contact.is_active = update.active
    db.commit()
    db.refresh(contact)
    return _contact_to_response(contact, db)


@app.delete("/contacts/{contact_name}")
def delete_contact(contact_name: str, db: Session = Depends(get_db)):
    contact = db.query(Contact).filter(Contact.contact_name == contact_name).first()
    if not contact:
        raise HTTPException(status_code=404, detail="Contact not found")
    db.delete(contact)
    db.commit()
    return {"message": f"Contact {contact_name} and all data deleted"}


@app.get("/contacts/{contact_name}/history")
def get_contact_history(contact_name: str, limit: int = 30, db: Session = Depends(get_db)):
    messages = db.query(Message).filter(
        Message.contact_name == contact_name
    ).order_by(Message.timestamp.desc()).limit(limit).all()
    return [
        {
            "msg_id": msg.msg_id,
            "role": msg.role,
            "content": msg.content,
            "timestamp": msg.timestamp.isoformat() if msg.timestamp else None,
            "category": msg.category,
            "status": msg.status,
        }
        for msg in messages
    ]


# =========================
# NOTES ENDPOINTS
# =========================

@app.get("/contacts/{contact_name}/notes", response_model=List[NoteResponse])
def get_notes(contact_name: str, db: Session = Depends(get_db)):
    """Alle Notizen für einen Kontakt (angepinnte zuerst)."""
    contact = db.query(Contact).filter(Contact.contact_name == contact_name).first()
    if not contact:
        raise HTTPException(status_code=404, detail="Contact not found")

    now = datetime.datetime.utcnow()
    notes = (
        db.query(Note)
        .filter(Note.contact_name == contact_name)
        .order_by(Note.pinned.desc(), Note.created_at.desc())
        .all()
    )
    return [
        NoteResponse(
            id=n.id,
            contact_name=n.contact_name,
            content=n.content,
            pinned=n.pinned,
            expiry_date=n.expiry_date.isoformat() if n.expiry_date else None,
            created_at=n.created_at.isoformat() if n.created_at else None,
            updated_at=n.updated_at.isoformat() if n.updated_at else None,
        )
        for n in notes
    ]


@app.post("/contacts/{contact_name}/notes", response_model=NoteResponse)
def create_note(contact_name: str, note: NoteCreate, db: Session = Depends(get_db)):
    """Neue Notiz für Kontakt erstellen."""
    contact = db.query(Contact).filter(Contact.contact_name == contact_name).first()
    if not contact:
        raise HTTPException(status_code=404, detail="Contact not found")

    expiry_dt = None
    if note.expiry_date:
        try:
            expiry_dt = datetime.datetime.fromisoformat(note.expiry_date)
        except ValueError:
            raise HTTPException(status_code=400, detail="Invalid expiry_date format")

    new_note = Note(
        contact_name=contact_name,
        content=note.content,
        pinned=note.pinned,
        expiry_date=expiry_dt
    )
    db.add(new_note)
    db.commit()
    db.refresh(new_note)
    return NoteResponse(
        id=new_note.id,
        contact_name=new_note.contact_name,
        content=new_note.content,
        pinned=new_note.pinned,
        expiry_date=new_note.expiry_date.isoformat() if new_note.expiry_date else None,
        created_at=new_note.created_at.isoformat() if new_note.created_at else None,
        updated_at=new_note.updated_at.isoformat() if new_note.updated_at else None,
    )


@app.patch("/contacts/{contact_name}/notes/{note_id}", response_model=NoteResponse)
def update_note(contact_name: str, note_id: int, update: NoteUpdate, db: Session = Depends(get_db)):
    """Notiz bearbeiten oder anpinnen."""
    note = db.query(Note).filter(Note.id == note_id, Note.contact_name == contact_name).first()
    if not note:
        raise HTTPException(status_code=404, detail="Note not found")
    if update.content is not None:
        note.content = update.content
    if update.pinned is not None:
        note.pinned = update.pinned
    if update.expiry_date is not None:
        try:
            note.expiry_date = datetime.datetime.fromisoformat(update.expiry_date)
        except ValueError:
            raise HTTPException(status_code=400, detail="Invalid expiry_date format")

    note.updated_at = datetime.datetime.utcnow()
    db.commit()
    db.refresh(note)
    return NoteResponse(
        id=note.id,
        contact_name=note.contact_name,
        content=note.content,
        pinned=note.pinned,
        expiry_date=note.expiry_date.isoformat() if note.expiry_date else None,
        created_at=note.created_at.isoformat() if note.created_at else None,
        updated_at=note.updated_at.isoformat() if note.updated_at else None,
    )


@app.delete("/contacts/{contact_name}/notes/{note_id}")
def delete_note(contact_name: str, note_id: int, db: Session = Depends(get_db)):
    """Notiz löschen."""
    note = db.query(Note).filter(Note.id == note_id, Note.contact_name == contact_name).first()
    if not note:
        raise HTTPException(status_code=404, detail="Note not found")
    db.delete(note)
    db.commit()
    return {"message": f"Note {note_id} deleted"}


# =========================
# EVENTS ENDPOINTS
# =========================

def _event_to_response(e: Event) -> EventResponse:
    return EventResponse(
        id=e.id,
        contact_name=e.contact_name,
        event_type=e.event_type.value,
        title=e.title,
        description=e.description,
        status=e.status.value,
        scheduled_at=e.scheduled_at.isoformat() if e.scheduled_at else None,
        generated_text=e.generated_text,
        sent_at=e.sent_at.isoformat() if e.sent_at else None,
        created_at=e.created_at.isoformat() if e.created_at else None,
    )


@app.get("/events", response_model=List[EventResponse])
def get_all_events(
    status: Optional[str] = Query(None),
    db: Session = Depends(get_db)
):
    """Alle Events, optional nach Status gefiltert."""
    query = db.query(Event)
    if status:
        try:
            query = query.filter(Event.status == EventStatus(status))
        except ValueError:
            raise HTTPException(status_code=400, detail=f"Invalid status: {status}")
    events = query.order_by(Event.scheduled_at.asc()).all()
    return [_event_to_response(e) for e in events]


@app.get("/contacts/{contact_name}/events", response_model=List[EventResponse])
def get_contact_events(contact_name: str, db: Session = Depends(get_db)):
    """Events für einen spezifischen Kontakt."""
    contact = db.query(Contact).filter(Contact.contact_name == contact_name).first()
    if not contact:
        raise HTTPException(status_code=404, detail="Contact not found")
    events = (
        db.query(Event)
        .filter(Event.contact_name == contact_name)
        .order_by(Event.scheduled_at.asc())
        .all()
    )
    return [_event_to_response(e) for e in events]


@app.post("/contacts/{contact_name}/events", response_model=EventResponse)
def create_event(contact_name: str, event: EventCreate, db: Session = Depends(get_db)):
    """Neues Event für Kontakt erstellen."""
    contact = db.query(Contact).filter(Contact.contact_name == contact_name).first()
    if not contact:
        raise HTTPException(status_code=404, detail="Contact not found")
    try:
        scheduled_dt = datetime.datetime.fromisoformat(event.scheduled_at)
        event_type_enum = EventType(event.event_type)
    except ValueError as err:
        raise HTTPException(status_code=400, detail=str(err))

    new_event = Event(
        contact_name=contact_name,
        event_type=event_type_enum,
        title=event.title,
        description=event.description,
        scheduled_at=scheduled_dt,
        generated_text=event.generated_text,
        status=EventStatus.PENDING,
    )
    db.add(new_event)
    db.commit()
    db.refresh(new_event)
    return _event_to_response(new_event)


@app.patch("/events/{event_id}", response_model=EventResponse)
def update_event(event_id: int, update: EventUpdate, db: Session = Depends(get_db)):
    """Event aktualisieren (Text, Datum, Status)."""
    event = db.query(Event).filter(Event.id == event_id).first()
    if not event:
        raise HTTPException(status_code=404, detail="Event not found")
    if update.title is not None:
        event.title = update.title
    if update.description is not None:
        event.description = update.description
    if update.generated_text is not None:
        event.generated_text = update.generated_text
    if update.scheduled_at is not None:
        event.scheduled_at = datetime.datetime.fromisoformat(update.scheduled_at)
    if update.status is not None:
        try:
            event.status = EventStatus(update.status)
            if event.status == EventStatus.SENT and not event.sent_at:
                event.sent_at = datetime.datetime.utcnow()
        except ValueError:
            raise HTTPException(status_code=400, detail=f"Invalid status: {update.status}")
    db.commit()
    db.refresh(event)
    return _event_to_response(event)


@app.delete("/events/{event_id}")
def delete_event(event_id: int, db: Session = Depends(get_db)):
    """Event löschen."""
    event = db.query(Event).filter(Event.id == event_id).first()
    if not event:
        raise HTTPException(status_code=404, detail="Event not found")
    db.delete(event)
    db.commit()
    return {"message": f"Event {event_id} deleted"}


@app.post("/events/{event_id}/trigger")
def trigger_event(event_id: int, db: Session = Depends(get_db)):
    """
    Markiert ein Event als TRIGGERED und gibt den generierten Text zurück.
    Android SyncWorker pollt diesen Endpoint und sendet die Nachricht dann via RemoteInput.
    """
    event = db.query(Event).filter(Event.id == event_id).first()
    if not event:
        raise HTTPException(status_code=404, detail="Event not found")
    if event.status != EventStatus.PENDING:
        raise HTTPException(status_code=400, detail=f"Event is already {event.status.value}")
    if not event.generated_text:
        raise HTTPException(status_code=400, detail="No generated_text for this event")

    event.status = EventStatus.TRIGGERED
    db.commit()
    db.refresh(event)

    return {
        "event_id": event.id,
        "contact_name": event.contact_name,
        "reply": event.generated_text,
        "title": event.title,
    }


# =========================
# LEGACY TASKS
# =========================
@app.get("/tasks/pending")
def get_pending_tasks(db: Session = Depends(get_db)):
    tasks = db.query(Task).filter(Task.status == "PENDING").all()
    return tasks


# =========================
# HEALTH
# =========================
@app.get("/health")
def health():
    return {"status": "ok", "version": "2.0.0"}