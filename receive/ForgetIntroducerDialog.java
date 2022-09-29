package org.thoughtcrime.securesms.trustedIntroductions.receive;

import android.app.AlertDialog;
import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;

import java.util.Date;

import static org.thoughtcrime.securesms.trustedIntroductions.TI_Utils.INTRODUCTION_DATE_PATTERN;

/**
 * Asks user if they really want to forget who made an introduction.
 */
public final class ForgetIntroducerDialog {

    private static final String TAG = Log.tag(ForgetIntroducerDialog.class);

    public interface ForgetIntroducer {
        void forgetIntroducer(@NonNull Long introductionId);
    }

    private ForgetIntroducerDialog() {
    }

    public static void show(@NonNull Context context, @NonNull Long introductionId, @NonNull String introduceeName, @NonNull String introducerName, @NonNull Date date, ForgetIntroducer f, @NonNull ManageActivity.IntroductionScreenType t) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context).setTitle(R.string.ForgetIntroucerDialog__Title);
        String text;
        switch(t){
            case ALL:
                text = context.getString(R.string.ForgetIntroucerDialog__Forget_Introducer_ALL, introducerName, introduceeName, INTRODUCTION_DATE_PATTERN.format(date));
                break;
            case RECIPIENT_SPECIFIC:
                text = context.getString(R.string.ForgetIntroucerDialog__Forget_Introducer_RECIPIENT_SPECIFIC, introducerName, introduceeName, INTRODUCTION_DATE_PATTERN.format(date));
                break;
            default:
                throw new AssertionError("Invalid management screen type!");
        }
        Log.e(TAG, text);
        builder.setMessage(text);
        builder.setNegativeButton(android.R.string.no, (dialog, which) -> dialog.dismiss())
               .setPositiveButton(R.string.ForgetIntroucerDialog__forget, (dialog, which) -> {
                   dialog.dismiss();
                   f.forgetIntroducer(introductionId);
               });
        builder.show();
    }
}