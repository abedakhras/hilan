<div dir="rtl">

# DECISIONS

## 1. החלטות ארכיטקטוניות
- **Backend – שלוש שכבות פשוטות: Controller → Service → Repository.** ה‑Controller רק ממפה HTTP, כל הלוגיקה העסקית ב‑`LeaveRequestService`. לא הוספתי interfaces ל‑service או mappers נפרדים – יש מימוש אחד ואין סיבה לשכבה נוספת.
- **שגיאות כ‑exceptions + `@RestControllerAdvice` אחד** שמחזיר `ProblemDetail` (RFC 9457, שהחליף את RFC 7807; מובנה ב‑Spring 6): 404 לא נמצא, 409 מצב לא חוקי, 400 קלט לא תקין. ככה ה‑service לא יודע כלום על HTTP, והשגיאות העסקיות, שגיאות הוולידציה ושגיאות פענוח הקלט מקבלות `detail` קריא.
- **Validation בשני מקומות:** `@NotNull` על ה‑DTO (צורה), וחוקים עסקיים (סוף לפני התחלה, יתרה) ב‑service.
- **Response DTO (`LeaveRequestResponse`)** במקום להחזיר את ה‑entity – מגדיר אילו שדות נחשפים בבקשות החופשה ומונע סריאליזציה ישירה של קשרי ה־entity. רשימת העובדים עדיין מחזירה `Employee`.
- **חישוב ימים מנוצלים ב‑DB** (`SUM` עם טווח תאריכים של השנה) במקום לטעון את כל הבקשות לזיכרון.
- **טבלת יתרות** (`GET /api/employees/balances?year=`) – לכל עובד: מכסת חופשה, מנוצל, ממתין, נשאר, וימי מחלה/ללא תשלום שנוצלו. חישוב בשאילתת `GROUP BY` אחת ב‑DB. רק לחופשה יש מכסה, אז למחלה ולל"ת מוצג רק כמה נוצל. הטבלה מתעדכנת אחרי אישור או הגשה.
- **Frontend:** services (`LeaveRequestService`, `EmployeeService`), מודלים עם enums במקום `any`, state ב‑signals, וכל subscription עם `takeUntilDestroyed`. הטופס בקומפוננטה נפרדת שמוציאה event `created`, והרשימה מעדכנת את עצמה מקומית – בלי רענון של הכל.

## 2. הבאג ביתרת החופשה
- **מה היה הבאג, איפה, ואיך תיקנתי:** ב‑`LeaveRequestsController.create` (שורה 79 במקור) הבדיקה הייתה `days > annualQuota` – כלומר רק האם הבקשה לבד גדולה מכל המכסה. המשתנה `used` חושב אבל אף פעם לא נכנס לבדיקה. בנוסף הוא סכם את כל הבקשות המאושרות מכל השנים. התיקון: `used + days > annualQuota`, כאשר `used` נספר רק לשנה של תאריך ההתחלה.
- **הטסט שמוכיח את התיקון:** `create_ExceedingRemainingBalance_IsRejected` – עובד עם 18/20 ימים מנסה לבקש 5, מקבל שגיאה ושום דבר לא נשמר. בנוסף טסט לגבול (בדיוק 2 ימים – עובר) וטסט ששנה קודמת לא נספרת.

## 3. אישור בקשה (approve) ו‑concurrency
- **מצבים לא חוקיים:** בקשה לא קיימת → 404. בקשה שכבר אושרה/נדחתה → 409 עם הודעה ("Leave request is already approved"). בנוסף בודקים שוב את היתרה ברגע האישור, כי ביצירה נספרים רק ימים מאושרים – שתי בקשות pending יכולות כל אחת לעבור את הבדיקה לבד ויחד לחרוג. במקרה כזה → 409.
- **שני אישורים במקביל:** כל האישור רץ בטרנזקציה אחת עם pessimistic lock (`SELECT ... FOR UPDATE`):
    1. נועלים את שורת הבקשה – שתי לחיצות על אותה בקשה לא יאשרו פעמיים.
    2. נועלים את שורת העובד – אישורים של אותו עובד רצים בתור. השני מחכה, ואחרי שהראשון עושה commit הוא סוכם מחדש (ב‑READ COMMITTED שאילתה חדשה רואה שינויים שבוצע להם commit לפני תחילתה) ומקבל 409.
    - בחרתי pessimistic ולא `@Version` כי אישורים נדירים ו‑contention נמוך, ואין צורך בלוגיקת retry. סדר הנעילות קבוע (בקשה ואז עובד), כדי להפחית סיכון ל־deadlock; מסלולים נוספים צריכים לשמור על סדר עקבי.
    - יש טסט עם שני threads ו‑`CountDownLatch` שמאשרים במקביל שתי בקשות של 6 ימים מול מכסה של 10 – בדיוק אחת עוברת. הטסט בודק גם שבמסד נשארה בקשה מאושרת אחת. שחרור שני threads יחד אינו מבטיח שחזור של המרוץ בכל הרצה; ההמתנה לתוצאות מוגבלת בזמן.

## 4. על מה ויתרתי בגלל הזמן
- **Authentication/Authorization** – כרגע כל אחד יכול לאשר כל בקשה, כולל את של עצמו. זה הדבר הראשון שהייתי מוסיף (Spring Security + תפקיד מנהל).
- **דחיית בקשה (reject)** – אין endpoint.
- **ימי עבודה** – כרגע נספרים ימים קלנדריים כולל סופ"ש וחגים. בקשה שחוצה שנה נספרת כולה לשנת ההתחלה.
- **חפיפה** – לא בודקים שלאותו עובד אין שתי בקשות על אותם תאריכים.
- **Migrations** – עדיין `ddl-auto: update`; הייתי עובר ל‑Flyway.
- **Enums נשמרים כ‑ORDINAL** ב‑DB – שינוי סדר ב‑enum ישבור נתונים. הייתי עובר ל‑`EnumType.STRING` (עם migration).
- **Frontend** – כתובת ה‑API קבועה בקוד (הייתי עובר ל‑environment/proxy), אין pagination, ואין e2e tests.
- **סדר עדיפויות להמשך:** הרשאות, דחיית בקשות, כללי ימי עבודה, Flyway ובדיקת e2e לתהליך המרכזי. לא בהכרח ניתן להשלים את כולם ביום אחד.

## 5. שימוש ב‑AI
### איפה AI עזר (כולל prompts)
1. prompt: **"Review the README and project structure first. Understand the current architecture, business flow, data model, and existing tests. Identify architectural, security, validation, and data-access issues, but don't change any code yet."**  
   → השתמשתי ב-AI קודם כדי להבין את הארכיטקטורה הקיימת (`Controller → Service → Repository`), את זרימת בקשות החופשה ואת מבנה ה-DB. מתוך הסקירה בניתי checklist שכלל בין היתר את הבאג ב-`used`, SQL Injection, חוסר validation, חשיפת entities ו-enums כ-ORDINAL.
2. prompt: **"Let's start with the leave-balance bug. First explain the root cause and the expected business behavior, then propose the smallest fix and add tests in the same style as the existing test suite. Don't refactor unrelated code."**  
   → תיקנתי את חישוב יתרת החופשה בצורה ממוקדת, כולל `used + days > annualQuota`, הגבלת החישוב לשנה הרלוונטית וטסטים למקרה חריגה, גבול ושנה קודמת.
3. prompt: **"Implement the remaining tasks while keeping the existing architecture simple. Move business logic from the controller to the service, keep transaction boundaries explicit, review concurrency for the approve flow, avoid unnecessary queries, preserve API compatibility with the frontend where possible, and add tests for every behavioral change."**  
   → השתמשתי ב-AI כדי לבצע את השינויים תוך שמירה על הארכיטקטורה: העברת הלוגיקה ל-`LeaveRequestService`, הוספת transaction ו-pessimistic locking ל-approve, שיפור שאילתות ה-DB, טיפול אחיד בשגיאות ועדכון ה-frontend. עברתי ידנית על ה-diff, הטסטים והתוצאה בדפדפן לפני כל commit.

### איפה דחיתי/תיקנתי הצעה של AI
- **חישוב היתרה:** החלפתי טעינת בקשות וסכימה ב־Java בשאילתת `SUM`, כדי לחשב במסד בלי לטעון entities מיותרים. בזמן אישור, השאילתה מתבצעת אחרי קבלת נעילת העובד. העדכניות תלויה גם במועד השאילתה וברמת הבידוד; עצם הסכימה ב־Java לא מוכיחה שהיה באג.
- **CSS שצובע את כל הטופס באדום:** הסלקטור `.ng-invalid.ng-touched` תפס גם את ה‑`<form>` עצמו, כך שכל המסגרת נצבעה אדום ולא רק השדות. זה נתפס רק בהרצה בדפדפן, לא בטסטים. הוגבל ל‑`input`/`select`.
- **Helper בטסט שיצר נתון לא חוקי:** `employeeWithUsedDays(10, 0)` יצר בקשה מאושרת של 0 ימים עם תאריך סיום לפני תאריך ההתחלה. הטסט עבר, אבל על נתון שלא יכול להתקיים. תוקן כך שלא ייווצר כלום כש‑`usedDays` הוא 0.
- **404 על approve למרות שהקוד תקין:** בהרצה מקומית ה‑endpoint לא הופיע ב‑Swagger. גוף השגיאה היה דף השגיאה של Spring ולא ה‑`ProblemDetail` שלנו – כלומר הבקשה לא הגיעה לקוד. הסיבה: `docker compose up` רץ על image ישן; `--build` פתר.

### אבטחה
- **SQL Injection** – `LeaveRequestsController.java` שורות 50–51 במקור (`/api/leave-requests/search`). הפרמטר `name` שורשר ישירות לתוך native SQL. קלט חיצוני יכול לשנות את משמעות השאילתה. היקף הפגיעה תלוי במבנה השאילתה, באופן הביצוע ובהרשאות מסד הנתונים. תיקון: query של Spring Data (`findByEmployee_NameContainingIgnoreCase...`) – הפרמטר נשלח כ‑bind parameter ו‑`%`/`_` עוברים escape. יש טסט (`search_DoesNotTreatInputAsSql`).
- **בלי authentication** – ראו סעיף 4 וסקירת האבטחה למעלה. זה הסיכון הגדול ביותר שנשאר.
- **חשיפת entities** – ה‑API החזיר את ה‑JPA entities ישירות. בבקשות החופשה הוחלף ב־DTO כך שרק השדות שבחרנו יוצאים החוצה; ברשימת העובדים נשאר המימוש המקורי.

### תיקונים בעקבות סקירת הקוד
- נעזרתי ב־AI לסקירה ולתיקון מקרי קצה: שמירת עדכונים מקומיים בזמן טעינת הרשימה, ביטול רענון יתרות קודם באמצעות `switchMap`, הגבלת שנה ל־1–9999, וטיפול אחיד בשגיאות פענוח קלט. נוספו בדיקות למקרים האלה.
- שגיאה ברענון יתרות נתפסת בתוך `switchMap`, כדי שלא תסגור את זרם הרענונים ותמנע ניסיון נוסף.

## 6. הוראות הרצה
- ללא שינוי מה‑README. שינויים ב‑API: `POST /api/leave-requests` מחזיר עכשיו 201, שגיאות חוזרות כ‑`ProblemDetail` (`{ status, title, detail }`), ובתשובה יש `employeeName` במקום אובייקט `employee`.

</div>
