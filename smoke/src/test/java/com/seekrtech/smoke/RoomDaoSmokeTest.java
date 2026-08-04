package com.seekrtech.smoke;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import androidx.room.Room;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 34)
public class RoomDaoSmokeTest {

    @Test
    public void insertAndQueryRoundTrip() {
        Context context = RuntimeEnvironment.getApplication();
        AppDatabase db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        try {
            Item item = new Item();
            item.name = "smoke";
            long id = db.itemDao().insert(item);
            Item loaded = db.itemDao().findById(id);
            assertNotNull(loaded);
            assertEquals("smoke", loaded.name);
        } finally {
            db.close();
        }
    }
}
