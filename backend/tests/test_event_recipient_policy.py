from backend.database import AutoMode, Contact, RelationType
from backend.main import event_recipient_auto_send_allowed


def make_contact(
    relation_type: str = RelationType.FRIEND.value,
    auto_mode: str = AutoMode.AUTO_LOW_RISK.value,
    is_active: bool = True,
) -> Contact:
    return Contact(
        display_name="Test Contact",
        relation_type=relation_type,
        auto_mode=auto_mode,
        is_active=is_active,
    )


def test_event_recipient_allows_active_auto_friend():
    allowed, reason = event_recipient_auto_send_allowed(make_contact())

    assert allowed is True
    assert "Approved" in reason


def test_event_recipient_blocks_inactive_contact():
    allowed, reason = event_recipient_auto_send_allowed(make_contact(is_active=False))

    assert allowed is False
    assert "inactive" in reason


def test_event_recipient_blocks_work_contact_even_when_auto_enabled():
    allowed, reason = event_recipient_auto_send_allowed(
        make_contact(relation_type=RelationType.WORK.value)
    )

    assert allowed is False
    assert "review" in reason


def test_event_recipient_blocks_review_mode_contact():
    allowed, reason = event_recipient_auto_send_allowed(
        make_contact(auto_mode=AutoMode.REVIEW.value)
    )

    assert allowed is False
    assert "auto mode" in reason
