# Cardify

Cardify is a JavaFX desktop application for turning an HTML ID-card template into a printable, data-driven workflow.

## What it does

1. Upload an HTML template that contains placeholders like `{{name}}`, `{{department}}`, or `{{photo}}`.
2. Download an Excel template generated from those placeholders.
3. Fill the Excel sheet with row data, including local image paths for image fields.
4. Import the completed workbook into the app.
5. Select one or more rows and print them to the connected printer.

## Project structure

The code is organized using a standard desktop-app layout:

- `src/main/java/org/example/cardify/MainApp.java` for the JavaFX launcher.
- `src/main/java/org/example/cardify/controller/` for UI orchestration.
- `src/main/java/org/example/cardify/service/` for template, Excel, and print logic.
- `src/main/java/org/example/cardify/model/` for row data objects.
- `src/main/java/org/example/cardify/util/` for reusable helpers.

## Build and run

```bash
mvn test
mvn javafx:run
```

## Template format

Use `{{placeholder_name}}` in the HTML file. If a placeholder value points to a local image file, Cardify converts it to a printable data URL before rendering.

QR code fields are also supported. If the placeholder name looks like a QR field, for example `qr_code`, `qr_id`, or `employee_qr`, Cardify renders that value as a QR code image.

If your template contains a QR placeholder, open the **Template & Excel** tab and choose which column should be encoded into that QR placeholder. The dropdown lists all template placeholders, so you can map a QR field like `qr_code` to another column such as `employee_id`.

For image fields, place the placeholder inside an image tag, for example:

```html
<img src="{{photo}}" alt="Employee photo" />
```

For QR code fields, use an image tag as well:

```html
<img src="{{qr_code}}" alt="QR code" />
```

## Excel format

The generated workbook uses the placeholder names as column headers. Fill one row per ID card. The first worksheet contains the data; the second worksheet contains instructions.
