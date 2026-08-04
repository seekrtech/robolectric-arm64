package com.seekrtech.smoke;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Dialog;
import android.view.WindowManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 34)
public class DialogWindowSmokeTest {

    @Test
    public void dialogShowsOnActivityWindow() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Dialog dialog = new Dialog(activity);
        dialog.show();

        assertTrue(dialog.isShowing());
        assertNotNull(dialog.getWindow());
        assertEquals(
                WindowManager.LayoutParams.TYPE_APPLICATION,
                dialog.getWindow().getAttributes().type);

        dialog.dismiss();
        assertFalse(dialog.isShowing());
    }
}
