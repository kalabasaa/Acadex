<div align="center">
  
  # Acadex
  
  A student platform for managing class schedules, academic activities, and reminders.

</div>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-Android-purple?logo=kotlin" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/XML-Layouts-orange?logo=xml" alt="XML"/>
  <img src="https://img.shields.io/badge/Gradle-Build%20System-02303A?logo=gradle" alt="Gradle"/>
  <img src="https://img.shields.io/badge/SQLite-Database-blue?logo=sqlite" alt="SQLite"/>
  <img src="https://img.shields.io/badge/Platform-Android-green?logo=android" alt="Android"/>
</p>

## About

Acadex is an Android student platform developed to help students organize their academic responsibilities in one place. It focuses on weekly class schedules, subject information, academic activities, and reminders.

The application uses Kotlin for functionality, XML for layouts, and SQLite for local data storage.

This project is developed as a **Practical Final Requirement for App Development**, applying Android development, database management, interface design, and version control.

## Features

- Subject information, including subject codes and professor names.
- Weekly class schedules from Monday to Sunday.
- Online and onsite class modes with meeting details or classroom locations.
- Academic activities such as assignments, projects, quizzes, and examinations.
- Reminders connected to classes and academic activities.
- Online school announcements (planned).

## Technology Stack

<p align="left">
  <img src="https://img.shields.io/badge/Kotlin-Programming%20Language-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/Android-Platform-3DDC84?logo=android&logoColor=white" alt="Android"/>
  <img src="https://img.shields.io/badge/XML-UI%20Layouts-E34F26?logo=xml&logoColor=white" alt="XML"/>
  <img src="https://img.shields.io/badge/SQLite-Database-003B57?logo=sqlite&logoColor=white" alt="SQLite"/>
  <img src="https://img.shields.io/badge/Android%20Studio-IDE-3DDC84?logo=androidstudio&logoColor=white" alt="Android Studio"/>
  <img src="https://img.shields.io/badge/Gradle-Build%20System-02303A?logo=gradle&logoColor=white" alt="Gradle"/>
  <img src="https://img.shields.io/badge/Git-Version%20Control-F05032?logo=git&logoColor=white" alt="Git"/>
  <img src="https://img.shields.io/badge/GitHub-Repository-181717?logo=github&logoColor=white" alt="GitHub"/>
</p>

## Security

Acadex uses a local SQLite database to store academic information on the device. Online features, including announcements, will require additional consideration for secure communication and data handling when implemented.

## Screenshots

Screenshots will be added as the application interface develops.

## Installation

1. Clone or download this repository.
2. Open the project in Android Studio.
3. Allow Gradle to sync.
4. Connect an Android device or start an emulator.
5. Run the application.

## Database Schema

Acadex currently uses or plans to implement the following database tables:

| Table | Description |
|---|---|
| `subjects` | Subject name, code, and professor |
| `classes` | Weekly meeting day, time, class mode, and location |
| `activities` | Academic tasks, deadlines, and status |
| `reminders` | Reminders associated with classes or activities |

Announcements are intended to come from an online service rather than being the primary source of data in the local database.

## Roadmap

- [x] Initialize the Android project.
- [x] Design the initial SQLite database schema.
- [x] Implement subject management.
- [x] Add and manage weekly class schedules.
- [x] Implement academic activity management.
- [ ] Implement reminders and notifications.
- [ ] Integrate online announcements.
- [ ] Complete the user interface.

## Academic Requirement

**Course:** App Development  
**Project:** Acadex  
**Requirement:** Practical Final Requirement

Acadex serves as the team's practical application of Android development concepts, local database management, interface design, and collaborative software development.

## Development Team

### Renier Tambogon 
**Lead Developer · Visual Identity Designer**

Responsible for leading the project's development, implementing application functionality, managing the database, and establishing Acadex's visual identity.


### Gunther Ordinario 
**Contributor · UI/UX Designer**

Contributes to the project through user interface and user experience design, helping shape the application's layout, usability, and overall user experience.

**GitHub:** [https://github.com/Clumsy0717]

## License

Copyright (c) 2026 Jhon Renier Tambogon

This project is licensed under the MIT License. You are free to use, copy, modify, merge, publish, distribute, sublicense, and sell copies of the software, provided that the original copyright notice and permission notice are included.

See the [LICENSE](LICENSE) file for details.
