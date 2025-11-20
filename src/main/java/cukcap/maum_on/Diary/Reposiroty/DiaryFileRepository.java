package cukcap.maum_on.Diary.Reposiroty;

import cukcap.maum_on.Home.Entity.DiaryFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiaryFileRepository extends JpaRepository<DiaryFile, Long> {
}