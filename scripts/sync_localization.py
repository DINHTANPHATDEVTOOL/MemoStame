#!/usr/bin/env python3
import json
import xml.etree.ElementTree as ET
import os

new_keys = {
    "trade_offer_status_pending": ("Pending Offer", "Đang chờ chấp nhận"),
    "trade_offer_status_accepted": ("Trade Accepted", "Đã chấp nhận trao đổi"),
    "audience_sheet_title": ("Who can see this stamp?", "Ai có thể xem tem này?"),
    "audience_sheet_subtitle": ("Choose who can view and interact with your memory stamp", "Chọn đối tượng có thể nhìn thấy và tương tác với con tem của bạn"),
    "audience_privacy_title": ("Privacy", "Quyền riêng tư"),
    "note_title_hint": ("Name this memory", "Đặt tên cho kỷ niệm"),
    "feed_filter_title": ("Memory Feed", "Bảng tin kỷ niệm"),
    "feed_stat_likes": ("%d likes", "%d lượt thích"),
    "feed_stat_comments": ("%d comments", "%d bình luận"),
    "feed_post_action": ("Post", "Đăng bài"),
    "feed_create_post_title": ("Create New Post", "Tạo bài viết mới"),
    "feed_composer_action_camera": ("New Stamp", "Chụp tem mới"),
    "feed_composer_action_post": ("Post Now", "Đăng ngay"),
    "feed_composer_no_stamp": ("No stamp selected. Tap to take a photo!", "Chưa có tem, bấm để chụp tem mới!"),
    "feed_action_liked": ("Liked", "Đã thích"),
    "feed_action_save_vault": ("Save to Vault", "Lưu tem"),
    "feed_save_vault_success": ("Stamp saved to your vault!", "Đã lưu con tem vào Kho của bạn!"),
    "feed_post_success": ("New memory posted to feed!", "Đã đăng bài viết mới lên Bảng tin!"),
    "time_days_ago": ("%d days ago", "%d ngày trước"),
    "time_hours_ago": ("%d hours ago", "%d giờ trước"),
    "time_minutes_ago": ("%d minutes ago", "%d phút trước"),
    "time_just_now": ("Just now", "Vừa xong"),
    "friends_tab_requests": ("Requests (%d)", "Lời mời (%d)"),
    "friends_theme_select": ("Select theme", "Chọn giao diện"),
    "friends_find_by_id": ("Find friend by ID", "Tìm bạn bằng ID"),
    "profile_stat_memories": ("MEMORIES", "KÝ ỨC"),
    "profile_visas_title": ("Visas & Sealed Memories", "Dấu Thị Thực & Ký Ức Đã Đóng"),
    "profile_visas_count": ("%d visas", "%d dấu"),
    "profile_share_passport": ("Share Passport", "Chia sẻ Passport"),
    "profile_change_cover": ("Change Cover", "Đổi hình nền"),
    "profile_change_avatar": ("Change Avatar", "Đổi avatar"),
    "profile_edit_display_name_bio": ("Edit display name & bio", "Chỉnh sửa tên hiển thị & tiểu sử"),
    "chat_status_seen": ("Seen", "Đã xem"),
    "chat_status_sent": ("Sent", "Đã gửi"),
    "chat_load_error": ("Unable to load conversation. Check connection and retry.", "Không thể tải cuộc trò chuyện. Kiểm tra kết nối và thử lại."),
    "chat_start_conversation_title": ("Start a conversation with %s", "Bắt đầu cuộc trò chuyện với %s"),
    "chat_start_conversation_hint": ("Send a message or attach a postal stamp to connect!", "Gửi tin nhắn hoặc đính kèm một con tem bưu chính để kết nối hoài niệm!"),
    "chat_default_recipient_name": ("Postal Friend", "Người bạn bưu chính"),
    "chat_choose_stamp_btn": ("Choose Stamp to Send", "Chọn con tem gửi ngay"),
    "chat_offline_cached_notice": ("Showing saved messages. Unable to sync.", "Đang hiển thị tin nhắn đã lưu. Chưa thể đồng bộ."),
    "chat_attached_stamp_label": ("Attached stamp:", "Đính kèm tem thư:"),
    "chat_input_hint_with_stamp": ("Write a note with your stamp…", "Viết lời nhắn kèm con tem…"),
    "chat_picker_title": ("Select Stamp to Attach", "Chọn tem để đính kèm tin nhắn"),
    "chat_picker_subtitle": ("Send your postal stamp to %s", "Gửi dấu ấn bưu chính của bạn cho %s"),
    "chat_picker_empty": ("Your stamp vault is empty. Take and create stamps first!", "Kho tem của bạn đang trống. Hãy chụp và tạo tem trước nhé!"),
    "chat_stamp_default_title": ("Commemorative Postal Stamp", "Dấu ấn tem kỷ niệm"),
    "chat_stamp_unsynced_msg": ("Stamp photo is not yet synced across devices.", "Ảnh tem chưa được đồng bộ để chia sẻ giữa các thiết bị."),
    "chat_stamp_sender_label": ("Sender: %s", "Người gửi: %s"),
    "chat_stamp_saved_toast": ("Stamp saved to your Vault successfully!", "Đã lưu con tem vào Kho của bạn thành công!"),
    "chat_stamp_save_btn": ("Save to Vault", "Lưu vào Kho tem"),
    "chat_stamp_tap_detail": ("Tap to view details ↗", "Chạm để xem chi tiết ↗"),
    "chat_login_required_stamp": ("Sign in required to share stamps via message.", "Cần đăng nhập để chia sẻ tem qua tin nhắn."),
    "friends_copy_id_success": ("Copied %s to clipboard!", "Đã sao chép %s vào bộ nhớ tạm!"),
    "friends_empty_title": ("Friends list is empty", "Danh sách bạn bè đang trống"),
    "friends_empty_desc": ("Share ID @%s or find friends by ID to send invitations!", "Chia sẻ ID @%s hoặc tìm kiếm ID bạn bè để gửi lời mời!"),
    "friends_copy_my_id": ("Copy your ID", "Sao chép ID của bạn"),
    "friends_find_new": ("Find new friends", "Tìm bạn mới"),
    "friends_my_id_label": ("Your MemoStamp ID", "ID MemoStamp của bạn"),
    "friends_search_exact_id_hint": ("Enter exact ID (e.g. @username)", "Nhập chính xác ID (ví dụ: @phat_memostamp)"),
    "friends_explore_users": ("Explore users (%d)", "Khám phá người dùng (%d)"),
    "friends_search_results": ("Search results (%d)", "Kết quả tìm kiếm (%d)"),
    "friends_no_users_found": ("No users found", "Chưa tìm thấy người dùng nào trên hệ thống"),
    "friends_no_users_with_id": ("No user found with ID \"%s\"", "Không tìm thấy người dùng nào với ID \"%s\""),
    "friends_invite_sent_to": ("Friend invitation sent to @%s!", "Đã gửi lời mời kết bạn đến @%s!"),
    "friends_invite_cancelled": ("Friend request cancelled", "Đã thu hồi lời mời kết bạn"),
    "friends_invite_accepted_with": ("You are now friends with @%s!", "Đã trở thành bạn bè với @%s!"),
    "friends_incoming_title": ("Incoming requests (%d)", "Lời mời kết bạn nhận được (%d)"),
    "friends_incoming_empty": ("No incoming friend requests", "Chưa có lời mời kết bạn nào gửi đến bạn"),
    "friends_wants_to_be_friends": ("Wants to connect with you", "Muốn kết bạn với bạn"),
    "friends_outgoing_title": ("Sent requests (%d)", "Lời mời đã gửi đi (%d)"),
    "friends_outgoing_empty": ("No pending sent requests", "Không có lời mời nào đang chờ duyệt"),
    "friends_waiting_acceptance": ("Waiting for acceptance…", "Đang chờ đối phương chấp nhận…"),
    "friends_cancel_request": ("Cancel", "Thu hồi"),
    "friends_inbox_empty_title": ("Mailbox has no stamps or postcards", "Hộp thư chưa có tem hoặc thiệp nào"),
    "friends_inbox_empty_desc": ("When friends gift or attach stamps in messages, they will appear here!", "Khi bạn bè gửi tặng tem hoặc đính kèm tem trong tin nhắn, con tem sẽ xuất hiện ở đây!"),
    "friends_trade_incoming_title": ("Incoming Trade Offers (%d)", "Lời mời trao đổi nhận được (%d)"),
    "friends_trade_outgoing_title": ("Sent Trade Offers (%d)", "Lời mời trao đổi đã gửi (%d)"),
    "friends_trade_empty_incoming": ("No incoming trade offers", "Không có lời mời trao đổi nào gửi đến bạn"),
    "friends_trade_empty_outgoing": ("No pending trade offers sent", "Không có lời mời trao đổi nào đang chờ"),
    "friends_trade_give": ("You give", "Bạn gửi tặng"),
    "friends_trade_receive": ("You receive", "Bạn nhận lại"),
    "friends_trade_complete_title": ("Trade Completed!", "Trao đổi thành công!"),
    "friends_trade_received_title": ("Stamps Received from Friends (%d)", "Tem đã nhận từ bạn bè (%d)"),
    "stamp_saved_vault_notice": ("Saved permanently in Stamp Vault", "Đã lưu vĩnh viễn trong Kho tem"),
    "stamp_owned_badge": ("Owned", "Đã sở hữu"),
    "friends_waiting_response": ("Waiting for response…", "Đang chờ phản hồi…"),
    "trade_offer_received": ("sent a stamp trade proposal!", "gửi lời đề nghị trao đổi tem!"),
    "trade_accepted_toast": ("Stamp trade accepted successfully!", "Đã chấp nhận trao đổi tem thành công!"),
    "friends_unfriend_confirm_msg": ("Are you sure you want to unfriend %s? You will need to send another request to connect again.", "Bạn có chắc muốn hủy kết bạn với %s? Sau khi hủy, bạn sẽ cần gửi lại lời mời kết bạn nếu muốn kết nối lại."),
    "friends_trade_offer_dialog_title": ("Postcard & Stamp from %s", "Bưu thiếp & Tem từ %s"),
    "friends_trade_accept_btn": ("Accept Stamp & Save to Vault", "Nhận Tem & Lưu Vào Kho"),
    "friends_trade_send_title": ("Send Stamp to @%s", "Gửi tặng tem cho @%s"),
    "friends_trade_select_stamp": ("Select a stamp from your Vault:", "Chọn một con tem từ Kho của bạn:"),
    "friends_trade_empty_vault": ("Vault is empty! Please take photos or create stamps first.", "Kho tem đang trống! Hãy chụp ảnh hoặc tạo tem trước."),
    "friends_trade_note_hint": ("Postal message to @%s", "Lời nhắn bưu chính gửi @%s"),
    "friends_trade_default_note": ("Gifting you this memory stamp!", "Tặng bạn dấu tem kỷ niệm này nhé!"),
    "friends_trade_send_btn": ("Send Letter & Stamp", "Gửi Thư & Tem"),
    "trade_btn": ("Gift Stamp", "Tặng tem"),
    "chat_message_prefix_you": ("You: %s", "Bạn: %s"),
    "profile_status_friend": ("FRIEND", "BẠN BÈ"),
    "profile_status_guest": ("GUEST", "KHÁCH"),
    "theme_selector_title": ("Select Theme Style", "Chọn Phong Cách Giao Diện"),
    "theme_selector_desc": ("Change display style to your preference", "Thay đổi phong cách hiển thị theo sở thích của bạn"),
    "theme_applied": ("Applied theme %s!", "Đã áp dụng phong cách %s!")
}

def escape_xml(s: str) -> str:
    # Escape & and ' and " for Android XML
    s = s.replace('&', '&amp;')
    s = s.replace("'", "\\'")
    s = s.replace('"', '\\"')
    return s

def unescape_for_strings(s: str) -> str:
    s = s.replace('&amp;', '&')
    s = s.replace("\\'", "'")
    s = s.replace('\\"', '"')
    return s

def escape_swift_strings(s: str) -> str:
    s = unescape_for_strings(s)
    s = s.replace('\\', '\\\\')
    s = s.replace('"', '\\"')
    return s

def main():
    en_tree = ET.parse('androidApp/src/main/res/values/strings.xml')
    vi_tree = ET.parse('androidApp/src/main/res/values-vi/strings.xml')

    pairs = {}
    for elem in en_tree.getroot().findall('string'):
        k = elem.get('name')
        t = elem.text or ''
        if k not in pairs:
            pairs[k] = [t, '']

    for elem in vi_tree.getroot().findall('string'):
        k = elem.get('name')
        t = elem.text or ''
        if k in pairs:
            pairs[k][1] = t

    # Add new keys
    for k, (en_v, vi_v) in new_keys.items():
        pairs[k] = [en_v, vi_v]

    print(f"Total unique keys to write: {len(pairs)}")

    # Sort keys deterministically while preserving logical grouping
    sorted_keys = sorted(pairs.keys())

    # Write Android values/strings.xml
    with open('androidApp/src/main/res/values/strings.xml', 'w', encoding='utf-8') as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n')
        for k in sorted_keys:
            val = pairs[k][0]
            # Android string escaping: escape quotes and unescaped ampersands
            escaped = val.replace('&', '&amp;').replace("'", "\\'").replace('"', '\\"')
            # Fix double escaping if any
            escaped = escaped.replace('&amp;amp;', '&amp;').replace('\\\\\'', "\\'").replace('\\\\"', '\\"')
            f.write(f'    <string name="{k}">{escaped}</string>\n')
        f.write('</resources>\n')

    # Write Android values-vi/strings.xml
    with open('androidApp/src/main/res/values-vi/strings.xml', 'w', encoding='utf-8') as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n')
        for k in sorted_keys:
            val = pairs[k][1]
            escaped = val.replace('&', '&amp;').replace("'", "\\'").replace('"', '\\"')
            escaped = escaped.replace('&amp;amp;', '&amp;').replace('\\\\\'', "\\'").replace('\\\\"', '\\"')
            f.write(f'    <string name="{k}">{escaped}</string>\n')
        f.write('</resources>\n')

    # Write iosApp Localizable.xcstrings
    xc_strings = {}
    for k in sorted_keys:
        en_v = unescape_for_strings(pairs[k][0])
        vi_v = unescape_for_strings(pairs[k][1])
        xc_strings[k] = {
            "comment": f"Localization key {k}",
            "extractionState": "manual",
            "localizations": {
                "en": {
                    "stringUnit": {
                        "state": "translated",
                        "value": en_v
                    }
                },
                "vi": {
                    "stringUnit": {
                        "state": "translated",
                        "value": vi_v
                    }
                }
            }
        }

    catalog = {
        "sourceLanguage": "en",
        "strings": xc_strings,
        "version": "1.0"
    }

    with open('iosApp/iosApp/Localizable.xcstrings', 'w', encoding='utf-8') as f:
        json.dump(catalog, f, indent=2, ensure_ascii=False)
        f.write('\n')

    # Write en.lproj/Localizable.strings
    os.makedirs('iosApp/iosApp/en.lproj', exist_ok=True)
    with open('iosApp/iosApp/en.lproj/Localizable.strings', 'w', encoding='utf-8') as f:
        f.write('/* MemoStamp English Strings */\n\n')
        for k in sorted_keys:
            val = escape_swift_strings(pairs[k][0])
            f.write(f'"{k}" = "{val}";\n')

    # Write vi.lproj/Localizable.strings
    os.makedirs('iosApp/iosApp/vi.lproj', exist_ok=True)
    with open('iosApp/iosApp/vi.lproj/Localizable.strings', 'w', encoding='utf-8') as f:
        f.write('/* MemoStamp Vietnamese Strings */\n\n')
        for k in sorted_keys:
            val = escape_swift_strings(pairs[k][1])
            f.write(f'"{k}" = "{val}";\n')

    print("Successfully synchronized all localization resources!")

if __name__ == '__main__':
    main()
