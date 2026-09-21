# ReaderLB

[![Android CI](https://github.com/aleksandreev2/readerlb/actions/workflows/android.yml/badge.svg?branch=main)](https://github.com/aleksandreev2/readerlb/actions/workflows/android.yml)
[![CodeQL](https://github.com/aleksandreev2/readerlb/actions/workflows/codeql.yml/badge.svg?branch=main)](https://github.com/aleksandreev2/readerlb/actions/workflows/codeql.yml)

ReaderLB — Android-приложение для безопасного импорта EPUB/TXT и переносимых пакетов ReaderLB в локальную библиотеку RanobeLib.

> **Статус:** pre-1.0. Проект уже пригоден для реального использования, но публичная стабильная release-цепочка ещё проходит финальную обкатку.

## Возможности

- импорт EPUB и TXT;
- автоматическое определение глав, обложки и иллюстраций;
- корректная поддержка главы 0 и дробных номеров глав;
- прямой импорт в локальную библиотеку RanobeLib через Storage Access Framework;
- безопасное добавление только отсутствующих глав в существующий тайтл;
- транзакционное обновление с восстановлением после прерывания;
- библиотека локальных тайтлов с поиском, сортировкой и обложками;
- удаление локальной новеллы с подтверждением;
- перенос новеллы между телефонами через `.readerlb.zip`;
- GitHub Releases updater с проверкой package name, подписи и SHA-256;
- опциональные push-уведомления о стабильных релизах через Firebase Cloud Messaging.

## Безопасность данных

ReaderLB работает с книгами локально. Исходные EPUB/TXT и содержимое библиотеки не отправляются на сервер ReaderLB.

Push-уведомления о релизах выключены по умолчанию. При включении Firebase получает технический FCM-токен, необходимый для доставки уведомлений. ReaderLB не использует Firebase Analytics.

Перед изменением существующего локального тайтла приложение проверяет структуру пакета и использует транзакционную запись. Уже существующие главы не перезаписываются автоматически.

## Установка

### Стабильные версии

Публичные стабильные сборки публикуются только через **GitHub Releases** и подписываются постоянным release-ключом.

До первого публичного stable-релиза тестовые APK доступны как artifacts в GitHub Actions.

### Android

Минимальная версия: **Android 10 (API 29)**.

На Android 11+ доступ к `Android/data` зависит от системного файлового менеджера и прошивки устройства. Если прямой доступ недоступен, ReaderLB может подготовить переносимый ZIP.

## Разработка

Требования:

- JDK 17;
- Android SDK 35;
- Gradle 8.9.

Основные проверки CI:

- unit tests;
- Android test compilation;
- Android Lint;
- debug APK;
- проверка сертификата подписи;
- R8/resource-shrunk APK;
- лимит размера APK;
- CodeQL-анализ Kotlin-кода.

Подробности: [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) и [docs/TESTING.md](docs/TESTING.md).

## Ветки и pull requests

`main` — единственная основная ветка и должна всегда оставаться пригодной к сборке и релизу.

Новая работа выполняется в короткоживущих ветках `feat/*`, `fix/*`, `docs/*` и `chore/*`. Изменения попадают в `main` через pull request после зелёного CI.

## Push-уведомления

Клиентская часть FCM встроена в приложение, но подписка выполняется только после явного согласия пользователя. Архитектура и настройка отправки релизных push описаны в [docs/PUSH_NOTIFICATIONS.md](docs/PUSH_NOTIFICATIONS.md).

## Участие в разработке

Перед pull request прочитайте [CONTRIBUTING.md](CONTRIBUTING.md).

Ошибки и предложения можно создавать через GitHub Issues. Общие вопросы — в [SUPPORT.md](SUPPORT.md). Для уязвимостей используйте инструкции из [SECURITY.md](SECURITY.md), а не публичный issue.

## Связанные документы

- [Roadmap](docs/ROADMAP.md)
- [Release process](docs/RELEASING.md)
- [Signing migration](docs/SIGNING_MIGRATION.md)
- [Testing](docs/TESTING.md)
- [Real corpus regressions](docs/REAL_CORPUS_REGRESSIONS.md)

## Отказ от аффилиации

ReaderLB — независимый проект и не является официальным приложением RanobeLib.

## Лицензия

Лицензия для публичного open-source релиза будет зафиксирована до 1.0. До добавления файла LICENSE исходный код доступен для просмотра, но отдельная лицензия на распространение ещё не предоставлена.
