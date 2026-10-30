# Hospital Emergency Triage Simulator (Java)

A console simulation of a hospital emergency room. Patients are admitted with a severity
(critical, urgent, normal); doctors work on separate threads and always treat the most urgent
waiting patient first.

## Concepts and where they are used

| Concept | Where |
|---|---|
| Inheritance | `Patient` -> `CriticalPatient`, `UrgentPatient`, `NormalPatient`; `Staff` -> `Doctor` -> `GeneralPhysician`, `Specialist`; `HospitalException` -> 3 sub-exceptions |
| Method overriding | `getPriority()`, `getTreatmentTimeMs()`, `getCategory()`, `getTimeFactor()` (each doctor type), `CriticalPatient.describe()` |
| Interfaces | `Treatable` (with a default method), `Comparable<Patient>`, `Runnable` (Doctor, PatientGenerator) |
| Collections | `PriorityBlockingQueue`, `ConcurrentHashMap`, `CopyOnWriteArrayList`, `TreeMap`, streams + `Collectors.groupingBy` (`EmergencyRoom.java`) |
| Multithreading | Each doctor is a thread (`ExecutorService`), `PatientGenerator` thread simulates arrivals, `AtomicInteger`, `volatile`, `synchronized` |
| Exception handling | Custom checked exceptions, multi-catch, `try/finally`, `try-with-resources`, bad CSV rows reported instead of crashing |
| Java NIO | `Path`, `Paths`, `Files.write/readAllLines/writeString/copy/move/createDirectories`, `DirectoryStream` (glob `*.csv`), `newBufferedReader`, `StandardOpenOption` (`HospitalStorage.java`) |

## Project structure

```
src/hospital/
  app/        Main.java                 menu and program entry
  model/      Patient + 3 subclasses, Treatable, PatientStatus
  staff/      Staff, Doctor, GeneralPhysician, Specialist
  service/    EmergencyRoom, PatientGenerator
  storage/    HospitalStorage           NIO file handling
  exception/  HospitalException and subclasses
```

## Run

Requires JDK 17 or newer.

```bash
mkdir out
javac -d out $(find src -name "*.java")      # Windows: javac -d out src\hospital\*\*.java
java -cp out hospital.app.Main
```

Data is stored in `hospital-data/` (`waiting.csv`, `treated.csv`, `activity.log`, `report.txt`, `backup/`, `intake/`).

## Try this

1. **Option 7** imports `intake/sample_patients.csv`. Two rows are bad on purpose (age 200, age "abc") and are reported and skipped.
2. **Option 2** shows the queue sorted by urgency, not arrival order.
3. **Option 3** starts the doctors (threads). Watch the live log of who treats whom.
4. **Option 8** runs the automatic demo: random patients arrive while 3 doctors work. Critical cases jump the queue.
5. **Option 9** prints and saves the statistics report. **Option 11** makes a backup.
6. Exit with patients still waiting, then restart: they are restored from `waiting.csv`.

## Screenshots

Add your screenshots to a `screenshots/` folder and link them here.
