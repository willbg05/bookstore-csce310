import flet as ft

def main(page: ft.page):

    page.title = "BookBag.py"

    page.padding = 24
    page.theme_mode = ft.ThemeMode.DARK

    heading = ft.Text(
        value = "BookBag.py",
        size = 28,
        weight = ft.FontWeight.BOLD,
    )

    search_field = ft.TextField(
        label="Search books",
        hint_text="Enter a title or author",
        expand=True,
    )

    status_text = ft.Text(
        value="Enter a search above to get started."
    )

    def handle_search(e):
        query = (search_field.value or "").strip()

        if not query:
            status_text.value = "Please enter a title or author."
        else:
            status_text.value = f'You searched for "{query}".'

        page.update()

    search_button = ft.Button(
        content="Search",
        on_click=handle_search,
    )

    search_row = ft.Row(
        controls=[
            search_field,
            search_button,
        ],
        spacing=12,
    )

    content = ft.Column(
        controls=[
            heading,
            search_row,
            status_text,
        ],
        spacing=20,
    )

    page.add(content)

if __name__ == "__main__":
    ft.run(main)