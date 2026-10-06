package olygym.app;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(InstallPlugin.class);
        registerPlugin(PrintPlugin.class);
        registerPlugin(DrivePlugin.class);
        registerPlugin(RestNotificationPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
