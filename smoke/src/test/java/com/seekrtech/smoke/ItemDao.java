package com.seekrtech.smoke;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

@Dao
public interface ItemDao {
    @Insert
    long insert(Item item);

    @Query("SELECT * FROM items WHERE id = :id")
    Item findById(long id);
}
