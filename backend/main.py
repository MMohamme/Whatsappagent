from __future__ import annotations

import datetime as dt
import json
import os
import random
from typing import Any, Dict, List, Optional

from dotenv import load_dotenv
from fastapi import Depends, FastAPI, Header, HTTPException, Query
from fastapi.responses import JSONResponse
from google import genai
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

try:
    from .database import (
        AgentDecision,
        AuditLog,
        AutoMode,
        Contact,
        ContactCategory,
        ContactRule,
        Draft,
        EventRecipient,
        EventTargetType,
        EventTicket,
        EventTicketStatus,
        Message,
        MessageStatus,
        Note,
        NoteScope,
        RiskLevel,
        SendAttempt,
        SendChannel,
        SessionLocal,
        init_db,
    )
    from .migrate import seed_database
except ImportError:
    from database import (
        AgentDecision,
        AuditLog,
        AutoMode,
        Contact,
        ContactCategory,
        ContactRule,
        Draft,
        EventRecipient,
        EventTargetType,
        EventTicket,
        EventTicketStatus,
        Message,
        MessageStatus,
        Note,
        NoteScope,
        RiskLevel,
        SendAttempt,
        SendChannel,
        SessionLocal,
        init_db,
    )
    from migrate import seed_database


load_dotenv()
init_db()
if os.getenv("SEED_ON_START", "1") == "1":
    seed_database()

app = FastAPI(title="WA Agent Backend", version="3.0.0")
APP_API_TOKEN = os.getenv("APP_API_TOKEN", "dev-token-change-me")
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")
client = genai.Client(api_key=GEMINI_API_KEY) if GEMINI_API_KEY else None


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def require_auth(authorization: Optional[str] = Header(default=None)):
    if not APP_API_TOKEN:
        return
    expected = f"Bearer {APP_API_TOKEN}"
    if authorization != expected:
        raise HTTPException(status_code=401, detail="Unauthorized")


def utcnow() -> dt.datetime:
    return dt.datetime.utcnow()


def parse_dt(value: Optional[str]) -> Optional[dt.datetime]:
    if not value:
        return None
    try:
        return dt.datetime.fromisoformat(value.replace("Z", "+00:00")).replace(tzinfo=None)
    except ValueError:
        raise HTTPException(status_code=400, detail=f"Invalid datetime: {value}")


def enum_value(value: Any) -> str:
    return value.value if hasattr(value, "value") else str(value)


class HistoryItem(BaseModel):
    role: str
    text: str


class InboundMessageRequest(BaseModel):
    custom_id: str
    sender_display_name: str
    text: str
    phone_number: Optional[str] = None
    package_name: Optional[str] = None
    notification_key: Optional[str] = None
    timestamp: Optional[str] = None
    history: List[HistoryItem] = Field(default_factory=list)


class AgentDecisionResponse(BaseModel):
    decision: str
    message_id: int
    draft_id: Optional[int] = None
    reply: Optional[str] = None
    risk_level: str
    reason: str
    recommended_delay_ms: int = 0


class ContactCreate(BaseModel):
    display_name: Optional[str] = None
    contact_name: Optional[str] = None
    phone_number: Optional[str] = None
    relation_type: str = "UNKNOWN"
    specific_relation: Optional[str] = None
    preferred_lang: str = "Deutsch"
    auto_mode: str = "REVIEW"
    categories: List[str] = Field(default_factory=list)
    style: Optional[str] = None
    behavior_rules: Optional[str] = None
    allowed_topics: Optional[str] = None
    blocked_topics: Optional[str] = None
    is_active: bool = True


class ContactUpdate(BaseModel):
    display_name: Optional[str] = None
    contact_name: Optional[str] = None
    phone_number: Optional[str] = None
    relation_type: Optional[str] = None
    specific_relation: Optional[str] = None
    preferred_lang: Optional[str] = None
    auto_mode: Optional[str] = None
    categories: Optional[List[str]] = None
    style: Optional[str] = None
    behavior_rules: Optional[str] = None
    allowed_topics: Optional[str] = None
    blocked_topics: Optional[str] = None
    is_active: Optional[bool] = None
    active: Optional[bool] = None


class NoteRequest(BaseModel):
    scope: str = "CONTACT"
    content: str
    contact_id: Optional[int] = None
    category: Optional[str] = None
    pinned: bool = False
    priority: int = 0
    valid_from: Optional[str] = None
    expires_at: Optional[str] = None


class EventTicketRequest(BaseModel):
    title: str
    target_type: str
    scheduled_at: str
    target_contact_id: Optional[int] = None
    target_category: Optional[str] = None
    base_text: Optional[str] = None
    prompt: Optional[str] = None
    stagger_min_seconds: int = 20
    stagger_max_seconds: int = 90


class SendAttemptRequest(BaseModel):
    channel: str = SendChannel.ANDROID_REMOTE_INPUT.value


class SendAttemptUpdate(BaseModel):
    status: str
    error: Optional[str] = None


class DraftDecisionUpdate(BaseModel):
    decision: str
    reply_text: Optional[str] = None
    reason: Optional[str] = None


def contact_to_dict(contact: Contact) -> Dict[str, Any]:
    return {
        "id": contact.id,
        "contact_name": contact.display_name,
        "display_name": contact.display_name,
        "phone_number": contact.phone_number,
        "relation_type": contact.relation_type,
        "specific_relation": contact.specific_relation,
        "preferred_lang": contact.preferred_lang,
        "behavior_rules": contact.rules.style if contact.rules else None,
        "is_active": contact.is_active,
        "active": contact.is_active,
        "auto_mode": contact.auto_mode,
        "categories": [c.name for c in contact.categories],
        "style": contact.rules.style if contact.rules else None,
        "allowed_topics": contact.rules.allowed_topics if contact.rules else None,
        "blocked_topics": contact.rules.blocked_topics if contact.rules else None,
        "replies_count": len([m for m in contact.messages if m.role == "assistant"]),
        "last_seen": contact.messages[-1].timestamp.isoformat() if contact.messages else None,
    }


def note_to_dict(note: Note) -> Dict[str, Any]:
    return {
        "id": note.id,
        "scope": note.scope,
        "contact_id": note.contact_id,
        "contact_name": note.contact.display_name if note.contact else "",
        "category": note.category,
        "content": note.content,
        "pinned": note.pinned,
        "priority": note.priority,
        "valid_from": note.valid_from.isoformat() if note.valid_from else None,
        "expires_at": note.expires_at.isoformat() if note.expires_at else None,
        "expiry_date": note.expires_at.isoformat() if note.expires_at else None,
        "created_at": note.created_at.isoformat() if note.created_at else None,
        "updated_at": note.updated_at.isoformat() if note.updated_at else None,
    }


def draft_to_dict(draft: Draft) -> Dict[str, Any]:
    return {
        "id": draft.id,
        "message_id": draft.message_id,
        "event_recipient_id": draft.event_recipient_id,
        "reply": draft.reply_text,
        "decision": draft.decision,
        "risk_level": draft.risk_level,
        "reason": draft.reason,
        "category": draft.category,
        "recommended_delay_ms": draft.recommended_delay_ms,
    }


def ticket_to_dict(ticket: EventTicket) -> Dict[str, Any]:
    return {
        "id": ticket.id,
        "title": ticket.title,
        "target_type": ticket.target_type,
        "target_contact_id": ticket.target_contact_id,
        "target_category": ticket.target_category,
        "scheduled_at": ticket.scheduled_at.isoformat(),
        "base_text": ticket.base_text,
        "prompt": ticket.prompt,
        "status": ticket.status,
        "recipients_count": len(ticket.recipients),
        "created_at": ticket.created_at.isoformat() if ticket.created_at else None,
    }


def recipient_to_dict(recipient: EventRecipient) -> Dict[str, Any]:
    return {
        "id": recipient.id,
        "ticket_id": recipient.ticket_id,
        "contact_id": recipient.contact_id,
        "contact_name": recipient.contact.display_name if recipient.contact else None,
        "draft_id": recipient.draft_id,
        "status": recipient.status,
        "scheduled_at": recipient.scheduled_at.isoformat(),
        "error": recipient.error,
        "draft": draft_to_dict(recipient.draft) if recipient.draft else None,
    }


def get_or_create_category(db: Session, name: str) -> ContactCategory:
    normalized = name.strip().upper()
    category = db.query(ContactCategory).filter(ContactCategory.name == normalized).first()
    if category:
        return category
    category = ContactCategory(name=normalized, label=normalized.title())
    db.add(category)
    db.flush()
    return category


def apply_contact_payload(db: Session, contact: Contact, payload: ContactCreate | ContactUpdate):
    data = payload.dict(exclude_unset=True)
    if data.get("contact_name") and not data.get("display_name"):
        data["display_name"] = data["contact_name"]
    if data.get("behavior_rules") and not data.get("style"):
        data["style"] = data["behavior_rules"]
    if "active" in data and "is_active" not in data:
        data["is_active"] = data["active"]
    for field in [
        "display_name",
        "phone_number",
        "relation_type",
        "specific_relation",
        "preferred_lang",
        "auto_mode",
        "is_active",
    ]:
        if field in data:
            setattr(contact, field, data[field])
    if "categories" in data and data["categories"] is not None:
        contact.categories = [get_or_create_category(db, c) for c in data["categories"]]
    if not contact.rules:
        contact.rules = ContactRule()
    for rule_field, attr in [
        ("style", "style"),
        ("allowed_topics", "allowed_topics"),
        ("blocked_topics", "blocked_topics"),
    ]:
        if rule_field in data:
            setattr(contact.rules, attr, data[rule_field])


def resolve_contact(db: Session, sender: str, phone_number: Optional[str]) -> Contact:
    contact = None
    if phone_number:
        contact = db.query(Contact).filter(Contact.phone_number == phone_number).first()
    if not contact:
        contact = db.query(Contact).filter(Contact.display_name == sender).first()
    if contact:
        return contact

    contact = Contact(
        display_name=sender,
        phone_number=phone_number,
        relation_type="UNKNOWN",
        preferred_lang="Deutsch",
        auto_mode=AutoMode.REVIEW.value,
        is_active=True,
    )
    contact.categories = [get_or_create_category(db, "UNKNOWN")]
    contact.rules = ContactRule(style="Neuer Kontakt. Antworte nicht automatisch.")
    db.add(contact)
    db.flush()
    return contact


SENSITIVE_KEYWORDS = {
    RiskLevel.CRITICAL.value: [
        "notfall",
        "polizei",
        "krankenhaus",
        "passwort",
        "code",
        "tan",
        "bank",
        "geld",
        "anwalt",
        "kuendigung",
        "kündigung",
        "vertrag",
        "schulden",
    ],
    RiskLevel.HIGH.value: [
        "arzt",
        "krank",
        "rechnung",
        "miete",
        "arbeit",
        "schicht",
        "chef",
        "streit",
        "beziehung",
    ],
}


def classify_risk(text: str, contact: Contact) -> tuple[str, str]:
    lower = text.lower()
    for risk, words in SENSITIVE_KEYWORDS.items():
        if any(word in lower for word in words):
            return risk, "Safety keyword matched"
    if contact.relation_type == "WORK":
        return RiskLevel.MEDIUM.value, "Work contact defaults to review"
    if contact.relation_type == "UNKNOWN":
        return RiskLevel.MEDIUM.value, "Unknown contact defaults to review"
    return RiskLevel.LOW.value, "Low-risk trusted contact"


def decide(contact: Contact, risk_level: str) -> tuple[str, str]:
    if not contact.is_active or contact.auto_mode == AutoMode.OFF.value:
        return AgentDecision.BLOCKED.value, "Contact is inactive or auto mode is OFF"
    if risk_level in {RiskLevel.CRITICAL.value, RiskLevel.HIGH.value}:
        return AgentDecision.NEEDS_REVIEW.value, "Safety gate requires review"
    if contact.auto_mode == AutoMode.REVIEW.value:
        return AgentDecision.NEEDS_REVIEW.value, "Contact auto mode is REVIEW"
    if contact.auto_mode == AutoMode.AUTO_LOW_RISK.value and risk_level == RiskLevel.LOW.value:
        return AgentDecision.AUTO_SEND_ALLOWED.value, "Low-risk message for trusted contact"
    if contact.auto_mode == AutoMode.AUTO_TRUSTED.value and risk_level in {RiskLevel.LOW.value, RiskLevel.MEDIUM.value}:
        return AgentDecision.AUTO_SEND_ALLOWED.value, "Trusted contact auto mode"
    return AgentDecision.NEEDS_REVIEW.value, "Default review fallback"


def active_notes_for_contact(db: Session, contact: Contact) -> List[Note]:
    now = utcnow()
    category_names = [c.name for c in contact.categories]
    query = db.query(Note).filter(
        (Note.valid_from.is_(None)) | (Note.valid_from <= now),
        (Note.expires_at.is_(None)) | (Note.expires_at > now),
    )
    notes = query.all()

    def applies(note: Note) -> bool:
        if note.scope == NoteScope.GLOBAL.value:
            return True
        if note.scope == NoteScope.CONTACT.value:
            return note.contact_id == contact.id
        if note.scope == NoteScope.CATEGORY.value:
            return (note.category or "").upper() in category_names
        return False

    filtered = [n for n in notes if applies(n)]
    return sorted(
        filtered,
        key=lambda n: (
            0 if n.scope == NoteScope.GLOBAL.value and n.pinned else 1,
            0 if n.scope == NoteScope.CATEGORY.value else 1,
            0 if n.scope == NoteScope.CONTACT.value else 1,
            -n.priority,
            n.created_at or utcnow(),
        ),
    )


def build_prompt(contact: Contact, inbound_text: str, history: List[HistoryItem], notes: List[Note]) -> str:
    relation = contact.specific_relation or contact.relation_type
    style = contact.rules.style if contact.rules and contact.rules.style else "Antworte freundlich, kurz und natuerlich."
    notes_text = "\n".join(f"- {note.content}" for note in notes) or "- Keine aktiven Notizen."
    history_text = "\n".join(f"{h.role}: {h.text}" for h in history[-12:]) or "Keine History."
    return f"""
Du bist Bassam/Mo und schreibst mit {contact.display_name}.
Beziehung: {relation}
Sprache: {contact.preferred_lang}
Stilregeln:
{style}

Aktive Notizen:
{notes_text}

Kontext:
{history_text}

Neue Nachricht:
{inbound_text}

Antworte strikt als JSON:
{{"reply":"...", "category":"...", "risk_level":"LOW|MEDIUM|HIGH|CRITICAL", "reason":"..."}}
"""


def generate_reply(contact: Contact, inbound_text: str, history: List[HistoryItem], notes: List[Note]) -> Dict[str, str]:
    fallback = {
        "reply": "Ich melde mich gleich dazu.",
        "category": "Allgemein",
        "risk_level": RiskLevel.LOW.value,
        "reason": "Fallback reply because LLM is not configured",
    }
    if not client:
        return fallback
    prompt = build_prompt(contact, inbound_text, history, notes)
    try:
        response = client.models.generate_content(
            model=os.getenv("GEMINI_MODEL", "gemini-2.5-flash-lite"),
            config={"system_instruction": "Du bist ein vorsichtiger WhatsApp Personal Companion."},
            contents=prompt,
        )
        raw = (response.text or "").replace("```json", "").replace("```", "").strip()
        data = json.loads(raw)
        if not data.get("reply"):
            return fallback | {"risk_level": RiskLevel.MEDIUM.value, "reason": "LLM returned empty reply"}
        return {
            "reply": str(data.get("reply", "")).strip(),
            "category": str(data.get("category", "Allgemein")).strip(),
            "risk_level": str(data.get("risk_level", RiskLevel.LOW.value)).strip().upper(),
            "reason": str(data.get("reason", "LLM generated reply")).strip(),
        }
    except Exception as exc:
        return fallback | {"risk_level": RiskLevel.MEDIUM.value, "reason": f"LLM parse/generation failed: {exc}"}


def recommended_delay(contact: Contact, decision_value: str) -> int:
    if decision_value != AgentDecision.AUTO_SEND_ALLOWED.value:
        return 0
    min_ms = contact.rules.delay_min_ms if contact.rules else 5000
    max_ms = contact.rules.delay_max_ms if contact.rules else 25000
    if min_ms >= max_ms:
        return min_ms
    return random.randint(min_ms, max_ms)


def audit(db: Session, action: str, entity_type: str, entity_id: Any, detail: str = ""):
    db.add(AuditLog(action=action, entity_type=entity_type, entity_id=str(entity_id), detail=detail[:500]))


@app.get("/health")
def health():
    return {"status": "ok", "version": "3.0.0"}


@app.post("/messages/inbound", response_model=AgentDecisionResponse, dependencies=[Depends(require_auth)])
def inbound_message(req: InboundMessageRequest, db: Session = Depends(get_db)):
    existing = db.query(Message).filter(Message.custom_id == req.custom_id).first()
    if existing:
        draft = existing.drafts[-1] if existing.drafts else None
        return AgentDecisionResponse(
            decision=draft.decision if draft else AgentDecision.IGNORE.value,
            message_id=existing.id,
            draft_id=draft.id if draft else None,
            reply=draft.reply_text if draft else None,
            risk_level=draft.risk_level if draft else RiskLevel.LOW.value,
            reason="Duplicate inbound message",
            recommended_delay_ms=draft.recommended_delay_ms if draft else 0,
        )

    contact = resolve_contact(db, req.sender_display_name, req.phone_number)
    message = Message(
        custom_id=req.custom_id,
        contact_id=contact.id,
        channel_message_id=req.notification_key,
        package_name=req.package_name,
        notification_key=req.notification_key,
        role="user",
        content=req.text,
        status=MessageStatus.RECEIVED.value,
        timestamp=parse_dt(req.timestamp) or utcnow(),
    )
    db.add(message)
    db.flush()

    base_risk, policy_reason = classify_risk(req.text, contact)
    notes = active_notes_for_contact(db, contact)
    generated = generate_reply(contact, req.text, req.history, notes)
    risk = generated.get("risk_level") if generated.get("risk_level") in RiskLevel._value2member_map_ else base_risk
    if base_risk in {RiskLevel.CRITICAL.value, RiskLevel.HIGH.value}:
        risk = base_risk
    decision_value, decision_reason = decide(contact, risk)
    delay = recommended_delay(contact, decision_value)
    status = MessageStatus.SEND_PENDING.value if decision_value == AgentDecision.AUTO_SEND_ALLOWED.value else MessageStatus.NEEDS_REVIEW.value
    if decision_value == AgentDecision.BLOCKED.value:
        status = MessageStatus.BLOCKED.value

    draft = Draft(
        message_id=message.id,
        reply_text=generated["reply"],
        decision=decision_value,
        risk_level=risk,
        reason=f"{decision_reason}; {generated.get('reason', policy_reason)}",
        category=generated.get("category", "Allgemein"),
        recommended_delay_ms=delay,
    )
    message.status = status
    message.category = draft.category
    db.add(draft)
    audit(db, "decision_created", "message", message.id, draft.reason or "")
    db.commit()
    db.refresh(message)
    db.refresh(draft)
    return AgentDecisionResponse(
        decision=decision_value,
        message_id=message.id,
        draft_id=draft.id,
        reply=draft.reply_text,
        risk_level=draft.risk_level,
        reason=draft.reason or "",
        recommended_delay_ms=delay,
    )


@app.post("/generate", dependencies=[Depends(require_auth)])
def legacy_generate(payload: Dict[str, Any], db: Session = Depends(get_db)):
    history = [
        HistoryItem(role=str(item.get("role", "user")), text=str(item.get("text", "")))
        for item in payload.get("history", [])
        if isinstance(item, dict)
    ]
    req = InboundMessageRequest(
        custom_id=str(payload.get("custom_id", "")),
        sender_display_name=str(payload.get("sender_display_name") or payload.get("sender") or "Unknown"),
        phone_number=payload.get("phone_number"),
        package_name=payload.get("package_name"),
        notification_key=payload.get("notification_key"),
        text=str(payload.get("text", "")),
        timestamp=payload.get("timestamp"),
        history=history,
    )
    response = inbound_message(req, db)
    if response.decision == AgentDecision.AUTO_SEND_ALLOWED.value:
        return {
            "reply": response.reply or "",
            "draft_id": str(response.draft_id or ""),
            "decision": response.decision,
        }
    return {
        "reply": response.reply or "",
        "draft_id": str(response.draft_id or ""),
        "decision": response.decision,
        "reason": response.reason,
    }


@app.get("/contacts", dependencies=[Depends(require_auth)])
def list_contacts(db: Session = Depends(get_db)):
    return [contact_to_dict(c) for c in db.query(Contact).order_by(Contact.display_name).all()]


@app.post("/contacts", dependencies=[Depends(require_auth)])
def create_contact(req: ContactCreate, db: Session = Depends(get_db)):
    if not (req.display_name or req.contact_name):
        raise HTTPException(status_code=400, detail="display_name/contact_name is required")
    contact = Contact()
    apply_contact_payload(db, contact, req)
    if not contact.categories:
        contact.categories = [get_or_create_category(db, req.relation_type)]
    db.add(contact)
    db.commit()
    db.refresh(contact)
    return contact_to_dict(contact)


def resolve_contact_ref(db: Session, contact_ref: str) -> Optional[Contact]:
    if contact_ref.isdigit():
        contact = db.get(Contact, int(contact_ref))
        if contact:
            return contact
    return db.query(Contact).filter(Contact.display_name == contact_ref).first()


@app.patch("/contacts/{contact_ref}", dependencies=[Depends(require_auth)])
def update_contact(contact_ref: str, req: ContactUpdate, db: Session = Depends(get_db)):
    contact = resolve_contact_ref(db, contact_ref)
    if not contact:
        raise HTTPException(status_code=404, detail="Contact not found")
    apply_contact_payload(db, contact, req)
    db.commit()
    db.refresh(contact)
    return contact_to_dict(contact)


@app.delete("/contacts/{contact_ref}", dependencies=[Depends(require_auth)])
def delete_contact(contact_ref: str, db: Session = Depends(get_db)):
    contact = resolve_contact_ref(db, contact_ref)
    if not contact:
        raise HTTPException(status_code=404, detail="Contact not found")
    db.delete(contact)
    db.commit()
    return {"message": f"Contact {contact_ref} deleted"}


@app.get("/notes", dependencies=[Depends(require_auth)])
def list_notes(
    scope: Optional[str] = Query(None),
    contact_id: Optional[int] = Query(None),
    category: Optional[str] = Query(None),
    active_only: bool = Query(False),
    db: Session = Depends(get_db),
):
    query = db.query(Note)
    if scope:
        query = query.filter(Note.scope == scope.upper())
    if contact_id is not None:
        query = query.filter(Note.contact_id == contact_id)
    if category:
        query = query.filter(Note.category == category.upper())
    if active_only:
        now = utcnow()
        query = query.filter((Note.valid_from.is_(None)) | (Note.valid_from <= now), (Note.expires_at.is_(None)) | (Note.expires_at > now))
    return [note_to_dict(n) for n in query.order_by(Note.pinned.desc(), Note.priority.desc(), Note.created_at.desc()).all()]


@app.post("/notes", dependencies=[Depends(require_auth)])
def create_note(req: NoteRequest, db: Session = Depends(get_db)):
    note = Note(
        scope=req.scope.upper(),
        contact_id=req.contact_id,
        category=req.category.upper() if req.category else None,
        content=req.content,
        pinned=req.pinned,
        priority=req.priority,
        valid_from=parse_dt(req.valid_from),
        expires_at=parse_dt(req.expires_at),
    )
    db.add(note)
    db.commit()
    db.refresh(note)
    return note_to_dict(note)


@app.patch("/notes/{note_id}", dependencies=[Depends(require_auth)])
def update_note(note_id: int, req: NoteRequest, db: Session = Depends(get_db)):
    note = db.get(Note, note_id)
    if not note:
        raise HTTPException(status_code=404, detail="Note not found")
    note.scope = req.scope.upper()
    note.contact_id = req.contact_id
    note.category = req.category.upper() if req.category else None
    note.content = req.content
    note.pinned = req.pinned
    note.priority = req.priority
    note.valid_from = parse_dt(req.valid_from)
    note.expires_at = parse_dt(req.expires_at)
    db.commit()
    db.refresh(note)
    return note_to_dict(note)


@app.delete("/notes/{note_id}", dependencies=[Depends(require_auth)])
def delete_note(note_id: int, db: Session = Depends(get_db)):
    note = db.get(Note, note_id)
    if not note:
        raise HTTPException(status_code=404, detail="Note not found")
    db.delete(note)
    db.commit()
    return {"status": "deleted", "id": note_id}


@app.get("/contacts/{contact_name}/notes", dependencies=[Depends(require_auth)])
def legacy_contact_notes(contact_name: str, db: Session = Depends(get_db)):
    contact = db.query(Contact).filter(Contact.display_name == contact_name).first()
    if not contact:
        raise HTTPException(status_code=404, detail="Contact not found")
    return [note_to_dict(n) for n in db.query(Note).filter(Note.contact_id == contact.id).all()]


@app.post("/contacts/{contact_name}/notes", dependencies=[Depends(require_auth)])
def legacy_create_contact_note(contact_name: str, req: NoteRequest, db: Session = Depends(get_db)):
    contact = db.query(Contact).filter(Contact.display_name == contact_name).first()
    if not contact:
        raise HTTPException(status_code=404, detail="Contact not found")
    req.scope = NoteScope.CONTACT.value
    req.contact_id = contact.id
    return create_note(req, db)


@app.delete("/contacts/{contact_name}/notes/{note_id}", dependencies=[Depends(require_auth)])
def legacy_delete_contact_note(contact_name: str, note_id: int, db: Session = Depends(get_db)):
    return delete_note(note_id, db)


def recipients_for_ticket(db: Session, ticket: EventTicket) -> List[Contact]:
    if ticket.target_type == EventTargetType.CONTACT.value:
        contact = db.get(Contact, ticket.target_contact_id)
        return [contact] if contact else []
    if ticket.target_type == EventTargetType.CATEGORY.value:
        category = (ticket.target_category or "").upper()
        return (
            db.query(Contact)
            .join(Contact.categories)
            .filter(ContactCategory.name == category, Contact.is_active == True)
            .order_by(Contact.display_name)
            .all()
        )
    return db.query(Contact).filter(Contact.is_active == True).order_by(Contact.display_name).all()


def event_reply_for(contact: Contact, ticket: EventTicket) -> str:
    if ticket.base_text:
        return ticket.base_text
    relation = contact.specific_relation or contact.relation_type
    return f"Alles Gute zu {ticket.title}, {contact.display_name}! Liebe Gruesse."


@app.post("/event-tickets", dependencies=[Depends(require_auth)])
def create_event_ticket(req: EventTicketRequest, db: Session = Depends(get_db)):
    ticket = EventTicket(
        title=req.title,
        target_type=req.target_type.upper(),
        target_contact_id=req.target_contact_id,
        target_category=req.target_category.upper() if req.target_category else None,
        scheduled_at=parse_dt(req.scheduled_at) or utcnow(),
        base_text=req.base_text,
        prompt=req.prompt,
        stagger_min_seconds=req.stagger_min_seconds,
        stagger_max_seconds=req.stagger_max_seconds,
    )
    db.add(ticket)
    db.commit()
    db.refresh(ticket)
    return ticket_to_dict(ticket)


@app.get("/event-tickets", dependencies=[Depends(require_auth)])
def list_event_tickets(status: Optional[str] = None, db: Session = Depends(get_db)):
    query = db.query(EventTicket)
    if status:
        query = query.filter(EventTicket.status == status.upper())
    return [ticket_to_dict(t) for t in query.order_by(EventTicket.scheduled_at.desc()).all()]


@app.get("/event-tickets/{ticket_id}", dependencies=[Depends(require_auth)])
def get_event_ticket(ticket_id: int, db: Session = Depends(get_db)):
    ticket = db.get(EventTicket, ticket_id)
    if not ticket:
        raise HTTPException(status_code=404, detail="Event ticket not found")
    data = ticket_to_dict(ticket)
    data["recipients"] = [recipient_to_dict(r) for r in ticket.recipients]
    return data


@app.post("/event-tickets/{ticket_id}/prepare", dependencies=[Depends(require_auth)])
def prepare_event_ticket(ticket_id: int, db: Session = Depends(get_db)):
    ticket = db.get(EventTicket, ticket_id)
    if not ticket:
        raise HTTPException(status_code=404, detail="Event ticket not found")
    if ticket.status == EventTicketStatus.CANCELLED.value:
        raise HTTPException(status_code=400, detail="Cancelled ticket cannot be prepared")

    contacts = recipients_for_ticket(db, ticket)
    existing_contact_ids = {r.contact_id for r in ticket.recipients}
    offset = 0
    for contact in contacts:
        if contact.id in existing_contact_ids:
            continue
        scheduled = ticket.scheduled_at + dt.timedelta(seconds=offset)
        offset += max(ticket.stagger_min_seconds, 1)
        recipient = EventRecipient(ticket_id=ticket.id, contact_id=contact.id, scheduled_at=scheduled)
        db.add(recipient)
        db.flush()
        draft = Draft(
            event_recipient_id=recipient.id,
            reply_text=event_reply_for(contact, ticket),
            decision=AgentDecision.NEEDS_REVIEW.value,
            risk_level=RiskLevel.LOW.value,
            reason="Category/event ticket requires review before mass send",
            category="Event",
            recommended_delay_ms=0,
        )
        db.add(draft)
        db.flush()
        recipient.draft_id = draft.id
    ticket.status = EventTicketStatus.PREPARED.value
    audit(db, "event_ticket_prepared", "event_ticket", ticket.id, f"{len(contacts)} recipients")
    db.commit()
    db.refresh(ticket)
    return get_event_ticket(ticket.id, db)


@app.post("/event-tickets/{ticket_id}/approve", dependencies=[Depends(require_auth)])
def approve_event_ticket(ticket_id: int, db: Session = Depends(get_db)):
    ticket = db.get(EventTicket, ticket_id)
    if not ticket:
        raise HTTPException(status_code=404, detail="Event ticket not found")
    for recipient in ticket.recipients:
        recipient.status = MessageStatus.SEND_PENDING.value
        if recipient.draft:
            recipient.draft.decision = AgentDecision.AUTO_SEND_ALLOWED.value
            recipient.draft.recommended_delay_ms = 0
    ticket.status = EventTicketStatus.APPROVED.value
    audit(db, "event_ticket_approved", "event_ticket", ticket.id)
    db.commit()
    return get_event_ticket(ticket.id, db)


@app.post("/event-tickets/{ticket_id}/cancel", dependencies=[Depends(require_auth)])
def cancel_event_ticket(ticket_id: int, db: Session = Depends(get_db)):
    ticket = db.get(EventTicket, ticket_id)
    if not ticket:
        raise HTTPException(status_code=404, detail="Event ticket not found")
    ticket.status = EventTicketStatus.CANCELLED.value
    for recipient in ticket.recipients:
        recipient.status = MessageStatus.SKIPPED.value
    db.commit()
    return get_event_ticket(ticket.id, db)


@app.get("/event-recipients/due", dependencies=[Depends(require_auth)])
def due_event_recipients(db: Session = Depends(get_db)):
    now = utcnow()
    recipients = (
        db.query(EventRecipient)
        .join(EventTicket)
        .filter(
            EventTicket.status == EventTicketStatus.APPROVED.value,
            EventRecipient.status == MessageStatus.SEND_PENDING.value,
            EventRecipient.scheduled_at <= now,
        )
        .order_by(EventRecipient.scheduled_at.asc())
        .all()
    )
    return [recipient_to_dict(r) for r in recipients]


@app.post("/drafts/{draft_id}/send-attempts", dependencies=[Depends(require_auth)])
def create_send_attempt(draft_id: int, req: SendAttemptRequest, db: Session = Depends(get_db)):
    draft = db.get(Draft, draft_id)
    if not draft:
        raise HTTPException(status_code=404, detail="Draft not found")
    attempt = SendAttempt(draft_id=draft_id, channel=req.channel, status=MessageStatus.SEND_PENDING.value, attempt_count=1)
    db.add(attempt)
    db.commit()
    db.refresh(attempt)
    return {"id": attempt.id, "draft_id": draft_id, "status": attempt.status, "channel": attempt.channel}


@app.patch("/send-attempts/{attempt_id}", dependencies=[Depends(require_auth)])
def update_send_attempt(attempt_id: int, req: SendAttemptUpdate, db: Session = Depends(get_db)):
    attempt = db.get(SendAttempt, attempt_id)
    if not attempt:
        raise HTTPException(status_code=404, detail="Send attempt not found")
    attempt.status = req.status.upper()
    attempt.error = req.error
    attempt.updated_at = utcnow()
    if attempt.status == MessageStatus.SENT.value:
        attempt.sent_at = utcnow()
    draft = attempt.draft
    if draft and draft.message:
        draft.message.status = attempt.status
    recipient = db.query(EventRecipient).filter(EventRecipient.draft_id == draft.id).first() if draft else None
    if recipient:
        recipient.status = attempt.status
        ticket = recipient.ticket
        statuses = [r.status for r in ticket.recipients]
        if statuses and all(s == MessageStatus.SENT.value for s in statuses):
            ticket.status = EventTicketStatus.SENT.value
        elif any(s == MessageStatus.FAILED.value for s in statuses):
            ticket.status = EventTicketStatus.PARTIAL_FAILED.value
        elif any(s == MessageStatus.SENDING.value for s in statuses):
            ticket.status = EventTicketStatus.SENDING.value
    db.commit()
    return {"id": attempt.id, "status": attempt.status, "sent_at": attempt.sent_at.isoformat() if attempt.sent_at else None}


@app.patch("/drafts/{draft_id}/decision", dependencies=[Depends(require_auth)])
def update_draft_decision(draft_id: int, req: DraftDecisionUpdate, db: Session = Depends(get_db)):
    draft = db.get(Draft, draft_id)
    if not draft:
        raise HTTPException(status_code=404, detail="Draft not found")
    draft.decision = req.decision.upper()
    if req.reply_text is not None:
        draft.reply_text = req.reply_text
    if req.reason is not None:
        draft.reason = req.reason
    db.commit()
    db.refresh(draft)
    return draft_to_dict(draft)


@app.get("/queue", dependencies=[Depends(require_auth)])
def queue(limit: int = Query(100, ge=1, le=500), db: Session = Depends(get_db)):
    drafts = db.query(Draft).order_by(Draft.created_at.desc()).limit(limit).all()
    return [draft_to_dict(d) for d in drafts]


@app.patch("/messages/{msg_id}", dependencies=[Depends(require_auth)])
def legacy_update_message_status(msg_id: str, status: str = Query(...), db: Session = Depends(get_db)):
    message = db.query(Message).filter(Message.custom_id == msg_id).first()
    if not message and msg_id.startswith("reply_"):
        message = db.query(Message).filter(Message.custom_id == msg_id.replace("reply_", "", 1)).first()
    if not message:
        raise HTTPException(status_code=404, detail="Message not found")
    normalized = status.upper()
    if normalized == "REPLY_SENT":
        normalized = MessageStatus.SENT.value
    elif normalized == "REPLY_FAILED":
        normalized = MessageStatus.FAILED.value
    elif normalized == "REPLY_PENDING":
        normalized = MessageStatus.SEND_PENDING.value
    message.status = normalized
    db.commit()
    return {"status": "ok", "msg_id": msg_id, "new_status": normalized}


@app.get("/contacts/{contact_name}/history", dependencies=[Depends(require_auth)])
def legacy_contact_history(contact_name: str, limit: int = 30, db: Session = Depends(get_db)):
    contact = db.query(Contact).filter(Contact.display_name == contact_name).first()
    if not contact:
        raise HTTPException(status_code=404, detail="Contact not found")
    messages = (
        db.query(Message)
        .filter(Message.contact_id == contact.id)
        .order_by(Message.timestamp.desc())
        .limit(limit)
        .all()
    )
    return [
        {
            "msg_id": m.custom_id,
            "role": m.role,
            "content": m.content,
            "timestamp": m.timestamp.isoformat() if m.timestamp else None,
            "category": m.category,
            "status": m.status,
        }
        for m in messages
    ]


@app.get("/events", dependencies=[Depends(require_auth)])
def legacy_events(status: Optional[str] = None, db: Session = Depends(get_db)):
    query = db.query(EventTicket)
    if status:
        mapped = status.upper()
        if mapped == "TRIGGERED":
            mapped = EventTicketStatus.APPROVED.value
        query = query.filter(EventTicket.status == mapped)
    return [
        {
            "id": t.id,
            "contact_name": t.target_category or str(t.target_contact_id or "GLOBAL"),
            "event_type": "CUSTOM",
            "title": t.title,
            "description": t.base_text or t.prompt,
            "status": t.status,
            "scheduled_at": t.scheduled_at.isoformat(),
            "generated_text": t.base_text,
            "sent_at": None,
            "created_at": t.created_at.isoformat() if t.created_at else None,
        }
        for t in query.order_by(EventTicket.scheduled_at.asc()).all()
    ]


@app.patch("/events/{event_id}", dependencies=[Depends(require_auth)])
def legacy_update_event(event_id: int, update: Dict[str, Any], db: Session = Depends(get_db)):
    ticket = db.get(EventTicket, event_id)
    if not ticket:
        raise HTTPException(status_code=404, detail="Event not found")
    if "status" in update and update["status"]:
        status = str(update["status"]).upper()
        if status == "SENT":
            ticket.status = EventTicketStatus.SENT.value
        elif status == "FAILED":
            ticket.status = EventTicketStatus.PARTIAL_FAILED.value
        elif status == "CANCELLED":
            ticket.status = EventTicketStatus.CANCELLED.value
    db.commit()
    return legacy_events(db=db)[0] if False else {"id": ticket.id, "status": ticket.status}


@app.get("/stats", dependencies=[Depends(require_auth)])
def stats(db: Session = Depends(get_db)):
    return {
        "contacts": {"total": db.query(Contact).count(), "active": db.query(Contact).filter(Contact.is_active == True).count()},
        "messages": {"total": db.query(Message).count(), "needs_review": db.query(Draft).filter(Draft.decision == AgentDecision.NEEDS_REVIEW.value).count()},
        "events": {"tickets": db.query(EventTicket).count(), "due": db.query(EventRecipient).filter(EventRecipient.status == MessageStatus.SEND_PENDING.value).count()},
    }
