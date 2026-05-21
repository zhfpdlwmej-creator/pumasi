package com.jacob.pumasi.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jacob.pumasi.model.Room;

public interface RoomRepository extends JpaRepository<Room, String> {

	List<Room> findAllByOrderByStartTimeAsc();
}
