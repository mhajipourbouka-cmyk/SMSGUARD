package ir.mahdihajipour.smsguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sees every incoming SMS with its real sender number (needs RECEIVE_SMS).
 * It decides spam / not spam, archives spam, then asks the listener to hide the notification.
 */
public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        SmsMessage[] parts = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (parts == null || parts.length == 0) return;

        // A long SMS arrives in several parts; join them per sender.
        Map<String, StringBuilder> bySender = new LinkedHashMap<>();
        for (SmsMessage m : parts) {
            if (m == null) continue;
            String from = m.getDisplayOriginatingAddress();
            if (from == null) from = "";
            StringBuilder b = bySender.get(from);
            if (b == null) {
                b = new StringBuilder();
                bySender.put(from, b);
            }
            String body = m.getDisplayMessageBody();
            if (body != null) b.append(body);
        }

        long now = System.currentTimeMillis();
        boolean anySpam = false;
        for (Map.Entry<String, StringBuilder> e : bySender.entrySet()) {
            String sender = e.getKey();
            String body = e.getValue().toString();
            Rules.Result r = Rules.classify(context, sender, body);
            Recent.add(Rules.normText(body), sender, r.spam, r.reason);
            if (r.spam) {
                anySpam = true;
                ArchiveDb.get(context).insertIfNew(sender, body, r.reason, now);
            }
        }
        if (anySpam) GuardListener.requestSweep();
    }
}
