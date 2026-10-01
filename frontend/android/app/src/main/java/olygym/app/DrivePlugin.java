package olygym.app;

import android.app.Activity;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;

import java.util.Collections;

/** Google Drive access for the coach-plan import: consent + Google's own native file picker,
 *  both drawn by Play services, then the access token handed back to JS. Only the drive.file scope
 *  is requested (non-sensitive; keeps the app out of Google verification), and the same scope
 *  serves the future Drive backup/sync feature. */
@CapacitorPlugin(name = "Drive")
public class DrivePlugin extends Plugin {

    private static final Scope DRIVE_FILE = new Scope("https://www.googleapis.com/auth/drive.file");

    private PluginCall pendingCall;
    private ActivityResultLauncher<IntentSenderRequest> pickerLauncher;

    @Override
    public void load() {
        // Google's consent + Picker UI is launched through an IntentSender, which Capacitor's
        // startActivityForResult (Intent-only) cannot carry. Register the StartIntentSenderForResult
        // contract here, in load(), so it exists before the activity reaches STARTED.
        pickerLauncher = getBridge().registerForActivityResult(
            new ActivityResultContracts.StartIntentSenderForResult(),
            this::onPickerResult);
    }

    @PluginMethod
    public void pickSpreadsheet(PluginCall call) {
        AuthorizationRequest request = AuthorizationRequest.builder()
            .setRequestedScopes(Collections.singletonList(DRIVE_FILE))
            .addResourceParameter(AuthorizationRequest.ResourceParameter.PICKER_OAUTH_TRIGGER, "true")
            .addResourceParameter(AuthorizationRequest.ResourceParameter.PICKER_MIMETYPES,
                "application/vnd.google-apps.spreadsheet,"
                + "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,text/csv")
            .setOptOutIncludingGrantedScopes(true)
            .build();

        pendingCall = call;
        Identity.getAuthorizationClient(getActivity()).authorize(request)
            .addOnSuccessListener(result -> {
                if (result.hasResolution()) {
                    IntentSenderRequest sender = new IntentSenderRequest.Builder(
                        result.getPendingIntent().getIntentSender()).build();
                    pickerLauncher.launch(sender);
                } else {
                    pendingCall = null;
                    resolve(call, result);
                }
            })
            .addOnFailureListener(e -> {
                pendingCall = null;
                call.reject("Google sign-in is unavailable: " + e.getMessage());
            });
    }

    private void onPickerResult(ActivityResult result) {
        PluginCall call = pendingCall;
        pendingCall = null;
        if (call == null) return;
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            call.reject("Cancelled");
            return;
        }
        try {
            resolve(call, Identity.getAuthorizationClient(getActivity())
                .getAuthorizationResultFromIntent(result.getData()));
        } catch (ApiException e) {
            call.reject("Cancelled or refused", e);
        }
    }

    private void resolve(PluginCall call, AuthorizationResult result) {
        JSObject out = new JSObject();
        out.put("accessToken", result.getAccessToken());
        Object picked = result.getTokenResponseParams() != null
            ? result.getTokenResponseParams().getString("picked_file_ids") : null;
        out.put("fileIds", picked == null ? "" : String.valueOf(picked));
        call.resolve(out);
    }
}
