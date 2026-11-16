from pymongo import MongoClient
from config import Config

# Creates client for the book part of the app
client = MongoClient(
    Config.MONGO_URI,
    serverSelectionTimeoutMS=5000
)

db = client[Config.MONGO_DB_NAME]

books_collection = db["books"]