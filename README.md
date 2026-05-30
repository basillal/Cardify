# Cardify

Cardify is a JavaFX desktop application for turning an HTML ID-card template into a printable, data-driven workflow.

## How to use

1. Open the app and go to the **Template & Excel** tab first.
2. Upload your HTML template.
3. Put placeholders like `{{name}}`, `{{department}}`, and `{{photo}}` inside the HTML where you want data to appear.
4. Check the detected placeholders list. If you already uploaded older templates, use the dropdown to switch to one of them.
5. Click **Download Excel Template**. Cardify creates a workbook with the same placeholder names as column headers.
6. Fill the workbook with one ID card per row.
7. Use normal text for normal fields, local file paths for image fields, and QR source text for QR-related columns.
8. Go to the **Data** tab and upload the completed Excel workbook.
9. Use the search box to find rows by any column value and use the status filter to show only the rows you want.
10. Select one or more rows and click **Print Selected Rows**.
11. Use **Template Preview** or **Row Preview** if you want to check the output before printing.
12. Use **Export Excel** to save filtered rows, **Remove Selected Template** to delete one saved template, or the **Danger Zone** to clear everything after captcha confirmation.

## What it does

Cardify turns an HTML card template into a print-ready workflow:

- uploads HTML templates with placeholders like `{{name}}`, `{{department}}`, or `{{photo}}`
- generates a matching Excel workbook from those placeholders
- imports filled Excel rows into a data table
- previews the template or a single row before printing
- prints selected rows to the connected printer
- keeps a history of saved templates so you can switch between them

## Template basics

- Use `{{placeholder_name}}` in the HTML file wherever you want data to appear.
- If the placeholder value is a local image path, Cardify converts it into a printable image.
- If the placeholder name looks like a QR field, Cardify generates a QR code image for it.
- Use `img` tags for photos and QR codes, for example `<img src="{{photo}}" />`.

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
