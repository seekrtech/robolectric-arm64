package com.seekrtech.smoke;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.os.Build;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 34)
public class RuntimeEnvironmentSmokeTest {

    @Test
    public void applicationAvailable() {
        assertNotNull(RuntimeEnvironment.getApplication());
    }

    @Test
    public void sdkIntMatchesConfig() {
        assertEquals(34, Build.VERSION.SDK_INT);
    }
}
