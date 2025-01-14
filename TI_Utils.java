package org.thoughtcrime.securesms.trustedIntroductions;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.json.JSONException;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.signal.core.util.Base64;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.fingerprint.Fingerprint;
import org.signal.libsignal.protocol.fingerprint.NumericFingerprintGenerator;
import org.thoughtcrime.securesms.crypto.ReentrantSessionLock;
import org.thoughtcrime.securesms.database.IdentityTable;
import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.RecipientRecord;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.trustedIntroductions.database.TI_Database;
import org.thoughtcrime.securesms.database.model.IdentityRecord;
import org.thoughtcrime.securesms.jobs.MultiDeviceVerifiedUpdateJob;
import org.thoughtcrime.securesms.trustedIntroductions.database.TI_IdentityTable;
import org.thoughtcrime.securesms.trustedIntroductions.glue.IdentityTableGlue;
import org.thoughtcrime.securesms.trustedIntroductions.glue.RecipientTableGlue;
import org.thoughtcrime.securesms.trustedIntroductions.jobs.TrustedIntroductionsReceiveJob;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;
import org.json.JSONArray;
import org.thoughtcrime.securesms.storage.StorageSyncHelper;
import org.thoughtcrime.securesms.util.IdentityUtil;
import org.whispersystems.signalservice.api.SignalSessionLock;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.util.Preconditions;

import static org.webrtc.ContextUtils.getApplicationContext;

// TODO: May be able to simplify further by using JsonUtil.java in codebase...
// Serialization for each object that I am sending is already present..

public class TI_Utils {

  // Prefix all logging with this tag for ease of search
  public static final String TI_LOG_TAG = "_TI:%s";
  static final String TAG = String.format(TI_LOG_TAG, Log.tag(TI_Utils.class));

  // Version, change if you change data/message format for compatibility
  // TODO: this is currently only reflected in message format, would need to add this to Database to make
  // Backup/Restore work accross revisions
  public static final String TI_MESSAGE_VERSION = "2.0";
  // Since the Signal version is still important and will not be overwritten I define my own
  // 1: major changes, 2: feature/ui changes , 3. bugs | stability fixes
  public static final String TI_APK_VERSION = "2.1.2";
  // text is the interim solution. In the future a custom mimetype should be used such that we can release a
  // custom interpreter that can be used by people that do not have the TI_extension installed.
  public static final String TI_MIME_TYPE = "text/plain";
  public static final String TI_MESSAGE_EXTENSION = ".trustedIntroduction";
  public static final String TI_MESSAGE_FILENAME = "Signal" + TI_MESSAGE_EXTENSION;

  // Random String to mark a message as a trustedIntroduction, since I'm tunneling through normal messages
  public static final String TI_IDENTIFYER = "QOikEX9PPGIuXfiejT9nC2SsDB8d9AG0dUPQ9gERBQ8qHF30Xj --- This message is part of an experimental feature and not meant to be read by humans --- Introduction Data:\n";
  // This should be added as a comment above and below each executed glue line in the Signal codebase
  // will aid in applying glue logic mechanically further down the line.
  // "TI_GLUE: eNT9XAHgq0lZdbQs2nfH /start"
  // "TI_GLUE: eNT9XAHgq0lZdbQs2nfH /end"
  static final String TI_GLUE_START = "TI_GLUE: eNT9XAHgq0lZdbQs2nfH start";
  static final String TI_GLUE_END = "TI_GLUE: eNT9XAHgq0lZdbQs2nfH end";
  static final String TI_SEPARATOR = "\n"; // marks start of JsonArray, human friendly
  static final int INDENT_SPACES = 1; // pretty printing for human readableness

  // For safety_number generation
  // @see VerifyDisplayFragment, iterations hardcoded there
  static final int ITERATIONS = 5200;
  // @See length of codes in VerifyDisplayFragment
  static final int SEGMENTS = 12;

  static final String UNDISCLOSED = "undisclosed";

  // Json keys
  // TODO: May want to add that to be part of the introduction at some point. This way we can avoid crashed on importing old backups with version missmatches
  static final String TI_VERSION_J = "ti_version";
  static final String INTRODUCER_J = "introducer";
  static final String INTRODUCEE_DATA_J = "introducees";
  static final String SERVICE_ID_J      = "service_ID";
  static final String NAME_J            = "name";
  static final String NUMBER_J = "number";
  static final String IDENTITY_J = "identity_key_base64";
  static final String PREDICTED_FINGERPRINT_J = "safety_number";

  // Job constants
  public static final long TI_JOB_LIFESPAN = TimeUnit.DAYS.toMillis(1);
  //public static final int TI_JOB_MAX_ATTEMPTS = Job.Parameters.UNLIMITED;
  public static final int TI_JOB_MAX_ATTEMPTS = 10;

  // How to format dates in introductions:
  @SuppressLint("SimpleDateFormat") public static final SimpleDateFormat INTRODUCTION_DATE_PATTERN = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");

  /**
   * /@see ManageListFragment::getFiltered()
   *
   * @param timestamp the timestamp as long
   * @return The date in 6 parts, in order of the format string, as strings
   */
  public static @NonNull TimestampDateParts splitIntroductionDate(long timestamp){
    String date = INTRODUCTION_DATE_PATTERN.format(timestamp);
    String[] dateTime = date.split(" ");
    String[] dateParts = dateTime[0].split("/");
    String[] timeParts = dateTime[1].split(":");
    return new TimestampDateParts(dateParts[0],
                                  dateParts[1],
                                  dateParts[2],
                                  timeParts[0],
                                  timeParts[1],
                                  timeParts[2]);
  }

  public static class TimestampDateParts {
    public String year;
    public String month;
    public String day;
    public String hours;
    public String minutes;
    public String seconds;

    public TimestampDateParts(String year, String month, String day, String hours, String minutes, String seconds){
      this.year = year;
      this.month = month;
      this.day = day;
      this.hours = hours;
      this.minutes = minutes;
      this.seconds = seconds;
    }
  }

  //@see VerifyDisplayFragment
  private static @NonNull String getFormattedSafetyNumbers(@NonNull Fingerprint fingerprint, int segmentCount) {
    String[]      segments = getSegments(fingerprint, segmentCount);
    StringBuilder result   = new StringBuilder();

    for (int i = 0; i < segments.length; i++) {
      result.append(segments[i]);

      if (i != segments.length - 1) {
        result.append(' ');
      }
    }

    return result.toString();
  }

  //@see VerifyDisplayFragment
  private static String[] getSegments(Fingerprint fingerprint, int segmentCount) {
    String[] segments = new String[segmentCount];
    String   digits   = fingerprint.getDisplayableFingerprint().getDisplayText();
    int      partSize = digits.length() / segmentCount;

    for (int i = 0; i < segmentCount; i++) {
      segments[i] = digits.substring(i * partSize, (i * partSize) + partSize);
    }

    return segments;
  }

  /**
   * Recreates the safety number that is generated between two recipients.
   * (used when sending intro, and to conveniently compute difference on conflict to expose in UI)
   * PRE: Nullable parameters must either ALL BE NULL or NONE BE NULL.
   * @param introductionRecipientId first Recipient
   * @param introduceeId second Recipient (introducee) => Must be present in the local database!
   * @param introduceeServiceId, fetched if null and needed
   * @param introduceeIdentityKey fetched if null
   * @return The expected safety number as a String, formated into segments identical to the VerifyDisplayFragment TODO: fix whacky formatting (some whitespaces missing)
   */
  public static String predictFingerprint(@NonNull RecipientId introductionRecipientId, @NonNull RecipientId introduceeId, @Nullable String introduceeServiceId, @Nullable IdentityKey introduceeIdentityKey) {
    if(introduceeServiceId == null && introduceeIdentityKey == null){
      // Fetch all the values
      LiveRecipient liveIntroducee = Recipient.live(introduceeId);
      Recipient introduceeResolved = liveIntroducee.resolve();
      introduceeServiceId = introduceeResolved.getServiceId().toString();
    } else if(introduceeServiceId != null && introduceeIdentityKey != null){
      //noop, normal case when recipient fetched through cursor
    } else {
      // TODO: Does that make sense??
      throw new AssertionError(TAG + "Unexpected non-null parameter in TI_Utils.predictFingerprint");
    }
    // Initialize introduction recipients id & key
    byte[]        introductionRecipientFingerprintId;
    byte[] introduceeFingerprintId;
    LiveRecipient live = Recipient.live(introductionRecipientId);
    Recipient introductionRecipientResolved = live.resolve();
    NumericFingerprintGenerator generator = new NumericFingerprintGenerator(ITERATIONS);
    Log.i(TAG, "using " + introductionRecipientResolved.requireServiceId());
    introductionRecipientFingerprintId = introductionRecipientResolved.requireServiceId().toByteArray();
    introduceeFingerprintId = introduceeServiceId.getBytes();
    IdentityKey introductionRecipientIdentityKey;
    try {
      introductionRecipientIdentityKey = getIdentityKey(introductionRecipientId);
    } catch(MissingIdentityException e) {
      Log.e(TAG, e.toString());
      throw new AssertionError(TAG + "The key of the introduction recipient must be present in the database at this stage. RecipientID: " + introductionRecipientId);
    }

    // @see VerifyDisplayFragment::initializeFingerprint(), iterations there also hardcoded to 5200 for FingerprintGenerator
    // @see ServiceId.java to understand how they convert the ACI to ByteArray
    // @see IdentityKey.java
    // Only version 2 is used since the migration to usernames
    Fingerprint fingerprint = generator.createFor(2,
                                                  introductionRecipientFingerprintId,
                                                  introductionRecipientIdentityKey,
                                                  introduceeFingerprintId,
                                                  introduceeIdentityKey);
    return getFormattedSafetyNumbers(fingerprint, SEGMENTS).replace("\n", "");
  }

  /**
   * Also used in  TrustedIntroductionsDatabase
   * @param id recipient ID
   * @return their identity as saved in the Identity database
   */
  public static IdentityKey getIdentityKey(RecipientId id) throws MissingIdentityException {
    Optional<IdentityRecord> identityRecord = AppDependencies.getProtocolStore().aci().identities().getIdentityRecord(id);
    if(identityRecord.isEmpty()){
      throw new MissingIdentityException(TAG + " No identity found for the recipient with id: " + id);
    }
    return identityRecord.get().getIdentityKey();
  }

  public static String encodeIdentityKey(IdentityKey key){
    return Base64.encodeWithoutPadding(key.serialize());
  }

  public static String getEncodedIdentityKey(RecipientId id) throws MissingIdentityException {
    return encodeIdentityKey(getIdentityKey(id));
  }

  @SuppressLint("Range") @WorkerThread
  public static String buildMessageBody(@NonNull RecipientId introducerRecipientId, @NonNull RecipientId introductionRecipientId, @NonNull Set<RecipientId> introducees) throws JSONException {
    if(introducees.size() <= 0){
      throw new AssertionError(TAG + " buildMessageBody called with no Introducees!");
    }

    JSONObject data = new JSONObject();

    Map<RecipientId, RecipientRecord> recipients = RecipientTableGlue.getRecordsForSendingTI(introducees);

    data.put(TI_VERSION_J, TI_MESSAGE_VERSION);

    // create Introducer entry
    JSONObject introducer = new JSONObject();
    Recipient resolvedIntroducer = Recipient.live(introducerRecipientId).get();

    introducer.put(NAME_J, getSomeNonNullName(introducerRecipientId, SignalDatabase.recipients().getRecord(introducerRecipientId)));
    introducer.put(NUMBER_J, resolvedIntroducer.getE164().isEmpty() ? UNDISCLOSED : resolvedIntroducer.getE164().get());
    introducer.put(SERVICE_ID_J, resolvedIntroducer.getServiceId().isEmpty() ? UNDISCLOSED : resolvedIntroducer.getServiceId().get().toString());
    try{
      introducer.put(PREDICTED_FINGERPRINT_J, predictFingerprint(introducerRecipientId,
                                                                 introductionRecipientId,
                                                                 Recipient.live(introductionRecipientId).get().requireServiceId().toString(),
                                                                 getIdentityKey(introductionRecipientId)));
    } catch (MissingIdentityException e){
      // should never be the case with the introducer
      throw new AssertionError(TAG + " My own identity key cannot be missing! ");
    }
    try {
      introducer.put(IDENTITY_J, encodeIdentityKey(getIdentityKey(introducerRecipientId)));
    } catch (MissingIdentityException e){
      // should never be the case with the introducer
      throw new AssertionError(TAG + " The introducers Identity cannot be missing! " + introducerRecipientId + " cannot be an introducer!");
    }
    data.put(INTRODUCER_J, introducer);

    // Now do the same for all introducees and wrap them in an array
    JSONArray introduceeData = new JSONArray();
    recipients.forEach((recipientId, recipientRecord) -> {
      try {
        JSONObject introducee = new JSONObject();
        introducee.put(NAME_J, getSomeNonNullName(recipientId, recipientRecord));
        String introduceeE164 = recipientRecord.getE164() == null ? UNDISCLOSED : recipientRecord.getE164();
        introducee.put(NUMBER_J, introduceeE164);
        ServiceId introduceeServiceId =  recipientRecord.getAci();
        if (introduceeServiceId == null){
          throw new AssertionError(TAG + "Introducee service ID may not be null.");
        }
        introducee.put(SERVICE_ID_J, introduceeServiceId);
        String formatedSafetyNR;
        try{
          IdentityKey introduceeIdentityKey = getIdentityKey(recipientId);
          introducee.put(IDENTITY_J, encodeIdentityKey(introduceeIdentityKey));
          formatedSafetyNR = predictFingerprint(introductionRecipientId, recipientId, introduceeServiceId.toString(), introduceeIdentityKey);
        } catch (MissingIdentityException e){
          e.printStackTrace();
          throw new AssertionError(TAG + " Unexpected missing identities when building TI message body!");
        }
        introducee.put(PREDICTED_FINGERPRINT_J, formatedSafetyNR);
        introduceeData.put(introducee);
        data.put(INTRODUCEE_DATA_J, introduceeData);
      } catch (JSONException e){
        e.printStackTrace();
        throw new AssertionError(TAG + "Json Error occured while building TI_message body.\n");
      }
    });
    return TI_IDENTIFYER + TI_SEPARATOR + data.toString(INDENT_SPACES);
  }


  private static String getSomeNonNullName(RecipientId id, RecipientRecord record){
    String name;
    name = record.getSystemDisplayName();
    if(name != null && !name.isEmpty()){
      return name;
    }
    name = record.getUsername();
    if(name != null && !name.isEmpty()){
      return name;
    }
    name = record.getEmail();
    if(name != null && !name.isEmpty()){
      return name;
    }
    Recipient rp = Recipient.resolved(id);
    name = rp.getDisplayName(getApplicationContext());
    if(name != null && !name.isEmpty()){
      return name;
    }
    name = rp.getProfileName().toString();
    if(name != null && !name.isEmpty()){
      return name;
    }
    return "¯\\_(ツ)_/¯";
  }

  // This structure allows for a oneliner in the processing logic to minimize additional code needed in there.
  public static void handleTIMessage(String message, long timestamp){
    // Schedule Reception Job
    AppDependencies.getJobManager().add(new TrustedIntroductionsReceiveJob(message, timestamp));
  }


  /**
   * @param id recipient Id for which the cache should be queried.
   * @return ACI as string if present, null otherwise
   */
  public static String getServiceIdFromRecipientId(RecipientId id){
    if (Recipient.live(id).resolve().getServiceId().isPresent()){
      return Recipient.live(id).resolve().getServiceId().get().toString();
    } else {
      return null;
    }
  }

  /**
   * PRE: message is a valid TI message (contains identifyer)
   * @param message the body of the .trustedintro attachment
   * @return a parsed JSONObject or null if there was a version missmatch
   */
  private static @Nullable JSONObject getPureJson(String message) throws JSONException{
    Preconditions.checkArgument(message.contains(TI_IDENTIFYER));
    if (isCorrectTImessageVersion(message)){
      return new JSONObject(message.replace(TI_IDENTIFYER, ""));
    } else {
     Log.e(TAG, "Invalid TI_message for the following body:\n" + message +  "\n\n--> The current version should be: " + TI_MESSAGE_VERSION + "\n");
     return null;
    }
  }

  /**
   * @param message the TI message (content of .trustedintro file)
   * @return True if the current TI_version is present in the message, false otherwise
   */
  private static boolean isCorrectTImessageVersion(String message){
    return message.contains(String.format(Locale.getDefault(), "\"ti_version\": \"%s\"", TI_MESSAGE_VERSION)) && message.contains(TI_IDENTIFYER);
  }

  /**
   * Parses the introducer recipient ID from the raw TI_message if possible, else null
   * @param message the TI message (content of .trustedintro file)
   * @return the RecipientId of the introducer or null if there was a version mismatch
   */
  public static @javax.annotation.Nullable RecipientId getIntroducerFromRawMessage(String message){
    try{
      JSONObject jsonData = getPureJson(message);
      if (jsonData != null){
        JSONObject introducer = new JSONObject(jsonData.getString(INTRODUCER_J));
        return RecipientId.from(ServiceId.parseOrThrow(introducer.getString(SERVICE_ID_J)));
      }
    } catch (JSONException e){
      Log.e(TAG, "A JsonException occured for the following TI message body: \n" + message);
      e.printStackTrace();
    }
    return null;
  }


  /**
   * Parses an incoming TI message to create introduction data
   * PRE: body is a valid TI message with the correct version.
   * @param body of the incoming message
   * @param timestamp when message was received
   * @param introducerId whom the message came from
   * @return populated List<TI_Data> if successfull, null otherwise
   */
  @WorkerThread
  @SuppressLint("Range") // keywords exists
  public static @Nullable List<TI_Data> constructIntroduceesFromTrustedIntrosString(String body, long timestamp, RecipientId introducerId){
    if (!body.contains(TI_IDENTIFYER) || !isCorrectTImessageVersion(body)){
      throw new AssertionError("Non TI message passed into constructIntroducees!");
    }
    String introducerServiceId = getServiceIdFromRecipientId(introducerId);
    ArrayList<TI_Data> result = new ArrayList<>();
    try {
      JSONObject data = getPureJson(body);
      if (data == null){
        // For now we just ignore introductions with mismatched versions or invalid bodies
        return null;
      }
      JSONArray introducees = data.getJSONArray(INTRODUCEE_DATA_J);
      ArrayList<IdKeyPair> idKeyPairs = new ArrayList<>();
      List<String> recipientServiceIds = new ArrayList<>();
      // Get all SerciveIds of introducees first to minimize database Queries
      for (int i = 0; i < introducees.length(); i++){
        JSONObject o = introducees.getJSONObject(i);
        String introduceeServiceId = o.getString(SERVICE_ID_J);
        idKeyPairs.add(new IdKeyPair(introduceeServiceId, o.getString(IDENTITY_J)));
        recipientServiceIds.add(introduceeServiceId);
      }
      // Get any known recipients & add to result
      Map<RecipientId, RecipientRecord> records   = RecipientTableGlue.getRecordsForReceivingTI(recipientServiceIds);
      ArrayList<String>                 knownIds = new ArrayList<>();
      if (!records.isEmpty()){
        records.forEach((recipientID, recipientRecord) -> {
          String introduceeServiceId = recipientRecord.getAci().toString();
          knownIds.add(introduceeServiceId);
          String name = getSomeNonNullName(recipientID, recipientRecord);
          String phone = recipientRecord.getE164() == UNDISCLOSED ? null : recipientRecord.getE164();
          String identityKey = IdKeyPair.findCorrespondingKeyInList(introduceeServiceId, idKeyPairs);
          TI_Data d = new TI_Data(null, TI_Database.State.PENDING, introducerServiceId, introduceeServiceId, name, phone, identityKey, null, timestamp);
          result.add(d);
        });
      }
      // Iterate through JSONData again, create the introductions for the still unknown recipients & set the predictedSecurityNumbers for all
      for(int i = 0; i < introducees.length(); i++){
        JSONObject o = introducees.getJSONObject(i);
        // If data was fetched from local database, simply add the Security number information
        String introduceeServiceId = o.getString(SERVICE_ID_J);
        if (knownIds.contains(introduceeServiceId)){
          int j = 0;
          while(!result.get(j).getIntroduceeServiceId().equals(introduceeServiceId)) j++;
          if (j >= result.size()){
            throw new AssertionError("Known Id not found in the original JSON Data");
          }
          result.get(j).setPredictedSecurityNumber(o.getString(PREDICTED_FINGERPRINT_J));
        } else {
          TI_Data d = new TI_Data(null, TI_Database.State.PENDING, introducerServiceId, o.getString(SERVICE_ID_J), o.getString(NAME_J), o.getString(NUMBER_J), o.getString(IDENTITY_J), o.getString(PREDICTED_FINGERPRINT_J), timestamp);
          result.add(d);
        }
      }
    } catch(JSONException e){
      Log.e(TAG, String.format("A JSON exception occured while trying to parse the TI message: %s", body));
      return null; // unsuccessful parse
    }
    return result;
  }

  private static String getPhone(JSONArray introducees, String introuceeServiceId){
    try {
      for (int i = 0; i < introducees.length(); i++) {
        JSONObject o = introducees.getJSONObject(i);
        if (o.getString(SERVICE_ID_J).equals(introuceeServiceId)){
          return o.getString(NUMBER_J);
        }
      }
    } catch (JSONException e){
      e.printStackTrace();
      Log.i(TAG, "Error while extracting phone nr. from introduction. Using placeholder.");
    }
    return "missing";
  }

  /**
   * //TODO: What are generally sensible queue strings? Does it matter if there are multiple vs. one queue for our purposes (e.g., multiple incoming)?
   * @param o Object, must be serializable.
   * @return A string that can be used for the Job queue key.
   */
  public static String serializeForQueue(Object o){
    MessageDigest md;
    String        hashtext;
    try {
      md = MessageDigest.getInstance("MD5");
      // Serialize introducee Set
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      ObjectOutputStream    oos = new ObjectOutputStream(bos);
      oos.writeObject(o);
      oos.flush();
      // Create digest and convert to Hex String
      byte[]     digest = md.digest(bos.toByteArray());
      BigInteger no     = new BigInteger(1, digest);
      hashtext = no.toString(16);
    } catch (NoSuchAlgorithmException e){
      Log.e(TAG, e.toString());
      Log.e(TAG, e.getMessage());
      throw new AssertionError("No such Algorithm!");
    } catch (IOException ioe){
      Log.e(TAG, ioe.toString());
      Log.e(TAG, ioe.getMessage());
      throw new AssertionError("IO exception!");
    }
    return hashtext;
  }

  private static class IdKeyPair{
    public String id;
    public String key;

    public IdKeyPair(String id, String key){
      this.id = id;
      this.key = key;
    }

    public static String findCorrespondingKeyInList(String id, ArrayList<IdKeyPair> list){
      for (IdKeyPair p: list) {
        if(id.equals(p.id)){
          return p.key;
        }
      }
      throw new AssertionError(TAG + " The Id you were searching for was not found in the list!");
    }
  }

  /**
   * Spawns it's own thread.
   * Used both by verifyDisplayFragment and Introduction database.
   * TODO: Should this be a job for persistence?
   *
   * @param status The new verification status
   */
  public static void updateContactsVerifiedStatus(RecipientId recipientId, IdentityKey remoteIdentity, TI_IdentityTable.VerifiedStatus status) {
    Log.i(TAG, "Saving identity: " + recipientId);
    SignalExecutors.BOUNDED.execute(() -> {
      // Fetch remote identity
      Recipient recipient = Recipient.live(recipientId).resolve();
      try (SignalSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
        // TI
        SignalDatabase.tiIdentityDatabase().setVerifiedStatus(recipientId, status);
        // Vanilla
        final boolean verified = TI_IdentityTable.VerifiedStatus.isVerified(status);
        if (verified) {
          AppDependencies.getProtocolStore().aci().identities()
                                 .saveIdentityWithoutSideEffects(recipientId,
                                                                 recipient.requireServiceId(),
                                                                 remoteIdentity,
                                                                 TI_IdentityTable.VerifiedStatus.toVanilla(status),
                                                                 false,
                                                                 System.currentTimeMillis(),
                                                                 true);
        } else {
          AppDependencies.getProtocolStore().aci().identities().setVerified(recipientId, remoteIdentity, IdentityTable.VerifiedStatus.forState(TI_IdentityTable.VerifiedStatus.toVanilla(status.toInt())));
        }
        // For other devices but the Android phone, we map the finer statusses to verified or unverified.
        // TODO: Change once we add new devices for TI
        AppDependencies.getJobManager()
                               .add(new MultiDeviceVerifiedUpdateJob(recipientId,
                                                                     remoteIdentity,
                                                                     IdentityTable.VerifiedStatus.forState(IdentityTableGlue.VerifiedStatus.toVanilla(status.toInt()))));
        StorageSyncHelper.scheduleSyncForDataChange();
        IdentityUtil.markIdentityVerified(getApplicationContext(), recipient, verified, false);
      }
    });
  }

  /**
   * //TODO: May need some cashing in TA_Data here
   * Query db and check if recipient is present. If not return Unknown.
   * PRE: serviceId represents valid ACI
   */
  @WorkerThread
  public static RecipientId getRecipientIdOrUnknown(String serviceId){
    ServiceId sId = ServiceId.parseOrThrow(serviceId);
    RecipientTable recipients = SignalDatabase.recipients();
    Optional<RecipientId> rId = recipients.getByServiceId(sId);
    return rId.orElse(RecipientId.UNKNOWN);
  }
}
