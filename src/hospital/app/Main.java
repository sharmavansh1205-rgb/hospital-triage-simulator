package hospital.app;

import hospital.exception.HospitalException;
import hospital.model.Patient;
import hospital.service.EmergencyRoom;
import hospital.service.PatientGenerator;
import hospital.staff.Doctor;
import hospital.storage.HospitalStorage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;

public class Main {

    private static final HospitalStorage storage = new HospitalStorage("hospital-data");
    private static final EmergencyRoom room = new EmergencyRoom(storage);
    private static final Scanner in = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("=== Hospital Emergency Triage Simulator ===");

        try {
            int restored = room.loadSaved();
            if (restored > 0) {
                System.out.println(restored + " waiting patient(s) restored from the last session.");
            }
            storage.createSampleIntake();
        } catch (IOException e) {
            System.out.println("Startup file problem: " + e.getMessage());
        }

        boolean running = true;
        while (running) {
            printMenu();
            String choice = in.hasNextLine() ? in.nextLine().trim() : "0";
            try {
                switch (choice) {
                    case "1" -> admitPatient();
                    case "2" -> showQueue();
                    case "3" -> startEr();
                    case "4" -> room.stop();
                    case "5" -> showTreated();
                    case "6" -> search();
                    case "7" -> room.importFromIntake().forEach(System.out::println);
                    case "8" -> runDemo();
                    case "9" -> showReport();
                    case "10" -> { room.save(); System.out.println("Saved."); }
                    case "11" -> backup();
                    case "12" -> storage.readLog(15).forEach(System.out::println);
                    case "0" -> running = false;
                    default -> System.out.println("Invalid option, try again.");
                }
            } catch (HospitalException e) {
                System.out.println("Error: " + e.getMessage());
                storage.log("Error: " + e.getMessage());
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            } catch (IOException e) {
                System.out.println("File problem: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                if (running) System.out.println();
            }
        }

        shutdown();
    }

    private static void shutdown() {
        try {
            if (room.isOpen()) {
                room.stop();
            }
            room.save();
            System.out.println("Data saved. Goodbye!");
        } catch (HospitalException | IOException e) {
            System.out.println("Problem while closing: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void printMenu() {
        System.out.println("""
                ---------------- MENU ----------------
                 1. Admit patient          7. Import patients (intake folder)
                 2. Show waiting queue     8. Run automatic demo
                 3. Start ER (doctors)     9. Statistics report
                 4. Stop ER               10. Save data
                 5. Show treated patients 11. Backup data
                 6. Search patient        12. View activity log
                 0. Save & exit
                --------------------------------------""");
        System.out.print("Choose: ");
    }

    private static String ask(String label) {
        System.out.print(label + ": ");
        return in.hasNextLine() ? in.nextLine().trim() : "";
    }

    private static void admitPatient() throws HospitalException {
        String name = ask("Name");
        int age = Integer.parseInt(ask("Age"));
        String code = ask("Category (C = critical, U = urgent, N = normal)");
        String complaint = ask("Complaint");
        Patient p = room.admit(name, age, code, complaint);
        System.out.println("Admitted: " + p.describe());
    }

    private static void showQueue() {
        List<Patient> queue = room.getWaitingSnapshot();
        if (queue.isEmpty()) {
            System.out.println("Waiting queue is empty.");
            return;
        }
        System.out.println("Waiting queue (most urgent first):");
        queue.forEach(p -> System.out.println(" " + p.describe()));
    }

    private static void startEr() throws HospitalException {
        int doctors = Integer.parseInt(ask("How many doctors (1-5)"));
        room.start(doctors);
        for (Doctor d : room.getDoctors()) {
            System.out.println(" On duty: " + d.describe());
        }
    }

    private static void showTreated() {
        List<Patient> done = room.getTreated();
        if (done.isEmpty()) {
            System.out.println("Nobody has been treated yet.");
            return;
        }
        for (int i = 0; i < done.size(); i++) {
            Patient p = done.get(i);
            System.out.printf("%2d. #%d %-12s [%-8s] by %-10s waited %.1f s%n",
                    i + 1, p.getId(), p.getName(), p.getCategory(), p.getTreatedBy(), p.getWaitedMs() / 1000.0);
        }
    }

    private static void search() {
        List<Patient> found = room.search(ask("Patient id or name"));
        if (found.isEmpty()) System.out.println("No match.");
        found.forEach(p -> System.out.println(" " + p.describe() + "  doctor: " + p.getTreatedBy()));
    }

    /** Arrival thread + doctor threads run together; then we show the order patients were completed. */
    private static void runDemo() throws HospitalException, InterruptedException {
        boolean startedHere = !room.isOpen();
        if (startedHere) {
            room.start(3);
        }
        int before = room.getTreated().size();

        Thread arrivals = new Thread(new PatientGenerator(room, 10, 150), "Arrival-Thread");
        arrivals.start();
        arrivals.join();

        boolean finished = room.awaitAllTreated(60_000);
        System.out.println(finished ? "\nAll patients treated." : "\nTimed out; some patients are still waiting.");

        List<Patient> done = room.getTreated();
        System.out.println("Order in which patients were completed (critical cases jump the queue):");
        for (int i = before; i < done.size(); i++) {
            Patient p = done.get(i);
            System.out.printf("%2d. %-12s [%-8s] by %s%n", i - before + 1, p.getName(), p.getCategory(), p.getTreatedBy());
        }
        if (startedHere) {
            room.stop();
        }
    }

    private static void showReport() throws IOException {
        List<String> report = room.buildReport();
        report.forEach(System.out::println);
        Path saved = storage.writeReport(report);
        System.out.println("Report saved to " + saved);
    }

    private static void backup() throws IOException {
        room.save();
        System.out.println("Backup created at: " + storage.backup());
    }
}
