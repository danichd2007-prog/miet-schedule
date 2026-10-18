package schedule.view;

import schedule.model.Schedule;
import schedule.model.ScheduleEntry;

public class ConsoleView {

    public void showSchedule(Schedule schedule) {
        System.out.println();
        System.out.println("========== РАСПИСАНИЕ ==========");

        if (schedule.getEntries().isEmpty()) {
            System.out.println("Расписание пусто.");
            return;
        }

        for (ScheduleEntry entry : schedule.getEntries()) {
            System.out.println(entry);
        }

        System.out.println("===============================");
    }

    public void showAddResult(String lessonName, boolean success) {
        if (success) {
            System.out.println("[OK] " + lessonName
                    + " добавлено в расписание.");
        } else {
            System.out.println("[ОШИБКА] " + lessonName
                    + " не удалось добавить в расписание.");
        }
    }
}