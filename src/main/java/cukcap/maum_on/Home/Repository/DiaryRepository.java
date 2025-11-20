package cukcap.maum_on.Home.Repository;

import cukcap.maum_on.Home.Entity.Diary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DiaryRepository extends JpaRepository<Diary, Long> {

    List<Diary> findAllByUserIdAndDiaryDateBetween(Long userId, LocalDate startDate, LocalDate endDate);

    Optional<Diary> findByUserIdAndDiaryDate(Long userId, LocalDate diaryDate);
}