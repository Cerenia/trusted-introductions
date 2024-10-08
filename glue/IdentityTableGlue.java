package org.thoughtcrime.securesms.trustedIntroductions.glue;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.thoughtcrime.securesms.database.IdentityTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.trustedIntroductions.database.TI_Database;
import org.thoughtcrime.securesms.trustedIntroductions.database.TI_IdentityTable;

public interface IdentityTableGlue {
  String TI_ADDRESS_PROJECTION    = IdentityTable.ADDRESS;
  String VERIFIED = IdentityTable.VERIFIED;
  String TABLE_NAME = IdentityTable.TABLE_NAME;


  static String getCreateTable(){
    return TI_IdentityTable.CREATE_TABLE;
  }

  static IdentityTableGlue createSingleton(Context c, SignalDatabase databaseHelper){
    return new TI_IdentityTable(c, databaseHelper);
  }


  /**
   *
   * @return Returns a Cursor which iterates through all contacts that are unlocked for
   * trusted introductions (for which @see VerifiedStatus.tiUnlocked returns true)
   */
  Cursor getCursorForTIUnlocked();

  /**
   * Publicly exposes verified status by recipient id.
   * @param id: id of the recipient
   * @return The VerifiedStatus of the recipient or default if the recipient is not in the database.
   */
  TI_IdentityTable.VerifiedStatus getVerifiedStatus(@Nullable RecipientId id);


  /**
    Adds a new identity to the shadow table
   */
  @WorkerThread
  boolean saveIdentity(@NonNull String addressName, @NonNull VerifiedStatus verifiedStatus);

  /**
   * Set the TI verification state of this recipient. If the recipient does not yet have an entry in the
   * DB, create one.
   * @param id: id of the recipient
   * @return Success of status change.
   */
  @WorkerThread
  boolean setVerifiedStatus(@NonNull RecipientId id, VerifiedStatus newStatus);

  enum VerifiedStatus{
    DEFAULT, MANUALLY_VERIFIED, UNVERIFIED, DIRECTLY_VERIFIED, INTRODUCED, DUPLEX_VERIFIED, SUSPECTED_COMPROMISE;

    public Integer toInt(){
      switch(this){
        case DEFAULT:
          return 0;
        case MANUALLY_VERIFIED:
          return 1;
        case UNVERIFIED:
          return 2;
        case DIRECTLY_VERIFIED:
          return 3;
        case INTRODUCED:
          return 4;
        case DUPLEX_VERIFIED:
          return 5;
        case SUSPECTED_COMPROMISE:
          return 6;
        default:
          return 2; // fail closed
      }
    }

    public static VerifiedStatus forState(Integer state){
      switch(state){
        case 0:
          return DEFAULT;
        case 1:
          return MANUALLY_VERIFIED;
        case 2:
          return UNVERIFIED;
        case 3:
          return DIRECTLY_VERIFIED;
        case 4:
          return INTRODUCED;
        case 5:
          return DUPLEX_VERIFIED;
        case 6:
          return SUSPECTED_COMPROMISE;
        default:
          return UNVERIFIED;
      }
    }

    public static Integer toVanilla(Integer status){
      VerifiedStatus s = forState(status);
      switch(s){
        case DEFAULT:
          return IdentityTable.VerifiedStatus.DEFAULT.toInt();
        case DIRECTLY_VERIFIED:
        case INTRODUCED:
        case DUPLEX_VERIFIED:
        case MANUALLY_VERIFIED:
          return IdentityTable.VerifiedStatus.VERIFIED.toInt();
        case UNVERIFIED:
        case SUSPECTED_COMPROMISE:
        default:
          return IdentityTable.VerifiedStatus.UNVERIFIED.toInt();
      }
    }

    public static IdentityTable.VerifiedStatus toVanilla(VerifiedStatus status){
      return IdentityTable.VerifiedStatus.forState(toVanilla(status.toInt()));
    }

    /**
     * Much of the code relies on checks of the verification status that are not interested in the finer details.
     * This function can now be called instead of doing 4 comparisons manually.
     * Do not use this to decide if trusted introductions are allowed.
     * @return True is verified, false otherwise.
     */
    public static boolean isVerified(VerifiedStatus status){
      switch (status){
        case DIRECTLY_VERIFIED:
        case INTRODUCED:
        case DUPLEX_VERIFIED:
        case MANUALLY_VERIFIED:
          return true;
        case DEFAULT:
        case UNVERIFIED:
        case SUSPECTED_COMPROMISE:
        default:
          return false;
      }
    }

    /**
     * Convenience function with id instead of status. Queries Disk.
     * @param id recipientID to be queried.
     * @return
     */
    public static boolean isVerified(RecipientId id){
      VerifiedStatus status = SignalDatabase.getInstance().getTiIdentityTable().getVerifiedStatus(id);
      return isVerified(status);
    }

    /**
     * Adding this in order to be able to change my mind easily on what should unlock a TI.
     * For now, only direct verification unlocks forwarding a contact's public key,
     * in order not to propagate malicious verifications further than one connection.
     *
     * @param status the verification status to be checked
     * @return true if strongly enough verified to unlock forwarding this contact as a
     * trusted introduction, false otherwise
     */
    public static Boolean ti_forwardUnlocked(VerifiedStatus status){
      switch(status){
        case DIRECTLY_VERIFIED:
        case DUPLEX_VERIFIED:
          return true;
        case INTRODUCED:
        case MANUALLY_VERIFIED:
        case DEFAULT:
        case UNVERIFIED:
        case SUSPECTED_COMPROMISE:
        default:
          return false;
      }
    }

    /**
     * A recipient can only receive TrustedIntroductions iff they have previously been strongly verified.
     * This function exists as it's own thing to allow for flexible changes. Queries DB for TI verification status.
     *
     * @param id The recipient ID.
     * @return True if this recipient can receive trusted introductions.
     */
    public static Boolean ti_recipientUnlocked(RecipientId id){
      VerifiedStatus status = SignalDatabase.tiIdentityDatabase().getVerifiedStatus(id);
      switch(status){
        case DIRECTLY_VERIFIED:
        case DUPLEX_VERIFIED:
          //INTRODUCED: false (if someone is being MiTmed, an introduction could be sensitive data. So you should be sure who you are talking to before you forward)
          //TODO: Both versions of this have their own pros and cons... Which one should it be?
          // for now, opting to unlock also on introduced in order to give more room to play for the study
        case INTRODUCED:
          return true;
        case MANUALLY_VERIFIED:
        case DEFAULT:
        case UNVERIFIED:
        case SUSPECTED_COMPROMISE:
        default:
          return false;
      }
    }

    /**
     * Returns true for any non-trivial positive verification status.
     * Used to prompt user when clearing a verification status that is not trivially recoverable and to decide
     * if a channel is secure enough to forward an introduction over.
     */
    public static Boolean stronglyVerified(VerifiedStatus status){
      switch(status){
        case DIRECTLY_VERIFIED:
        case DUPLEX_VERIFIED:
        case INTRODUCED:
          return true;
        case MANUALLY_VERIFIED:
        case DEFAULT:
        case UNVERIFIED:
        case SUSPECTED_COMPROMISE:
        default:
          return false;
      }
    }
  }

  /**
   *
   * @param introduceeServiceId The service ID of the introducee that may get their verification state modified.
   * @param previousIntroduceeVerification The previous introducee verification state.
   * @param newState The new state of the modified introduction.
   * @param logmessage What to print to logcat if the verification state was modified.
   */
  void modifyIntroduceeVerification(String introduceeServiceId, TI_IdentityTable.VerifiedStatus previousIntroduceeVerification, TI_Database.State newState, String logmessage);
}
