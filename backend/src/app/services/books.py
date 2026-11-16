import re

from bson import ObjectId
from database import books_collection
from pymongo import TEXT

# For generic search, no advanced filtering
books_collection.create_index(
    [("title", TEXT), ("author", TEXT),],
    weights={
        "title": 5,
        "author": 3,
    },
    name="book_search_text",
)

def serialize_book(book):
    """
    Converts a MongoDB document into a dictionary for JSON-compatibility
    """
    result = dict(book) # Copy to not change the original dict
    result["_id"] = str(result["_id"]) # Flask can return str ID as JSON

    return result

def norm_isbn(isbn):
    """
    Normalizes the ISBN to a string containing only numbers
    """
    return isbn.strip().replace("-", "").replace(" ", "").upper()

def create_book(title: str, auths: str, isbn: str, c_avail: int) -> str:
    """
    Inserts new book into the mongo book collection, returns it's ID as a string

    Args:
        title: The book's title, a `str`
        auths: The book's author(s), a `str`
        isbn: The book's ISBN, a `str`
        c_avail: The number of available copies of that book, an `int`
    """

    doc = {
        "title": title,
        "authors": auths,
        "isbn": isbn,
        "copies_available": c_avail
    }

    result = books_collection.insert_one(doc)

    return str(result.inserted_id)

def get_book(book_id):
    """
    Find a book by its Mongo ID, returns a serialized book document (JSON)

    Args:
        book_id: The ID of the book being searched for, a `str`
    """

    if not ObjectId.is_valid(book_id):
        return None

    doc = books_collection.find_one({
        "_id": ObjectId(book_id)
    })

    if doc is None:
        return None

    return serialize_book(doc)

def search_books(title="", author="", isbn="", search=""):
    """
    Return up to 50 books, either generically searching based on pre-specified weights for title and author, 
    or by using optional advanced filtering by title, author, and/or isbn.

    Args:
        title: The title of the book being searched for, an *optional* `str`
        author: The author of the book being searched for, an *optional* `str`
        price: The price of the book being searched for, an *optional* `str`
        : The author of the book being searched for, an *optional* `str`
    """

    # TODO: Implement filtering multiple authors
    # TODO: Implement sorting search results based on:
    #           - Relevance (Default)
    #           - Ratings (High to Low)
    #           - Ratings(Low to High)
    #           - Price (High to Low)
    #           - Price (Low to High)

    query = {}

    if title and title.strip():
        query["title"] = {
            "$regex": re.escape(title.strip()),
            "$options": "i" # Make the match case-insensitive.
        }

    if author and author.strip():
        query["authors"] = {
            "$regex": re.escape(author.strip()),
            "$options": "i" # Make the match case-insensitive.
        }

    # Doesn't need regex, exact matches only
    if isbn and isbn.strip():
        query["isbn"] = norm_isbn(isbn) 

    search = (search or "").strip()

    if search:
        query["$text"] = {"$search": search}

        cursor = (
            books_collection
            .find(query, {"score": {"$meta": "textScore"}})
            .sort([
                ("score", {"$meta": "textScore"}),
                ("title", 1),
                ("_id", 1),
            ])
            .limit(50)
        )

    else:
        cursor = (
            books_collection
            .find(query)
            .sort([("title", 1), ("_id", 1)])
            .limit(50)
        )

    return [serialize_book(book) for book in cursor]