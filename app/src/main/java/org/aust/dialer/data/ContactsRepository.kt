package org.aust.dialer.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import org.aust.dialer.core.PhoneUtils
import java.text.Collator

/** Reads the device's own Contacts Provider. No separate contacts database is kept. */
class ContactsRepository(private val context: Context) {

    fun hasReadPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private class Builder(
        val id: Long,
        val lookupKey: String?,
        val name: String,
        val photoUri: String?,
        val thumbUri: String?,
        val starred: Boolean,
    ) {
        val numbers = ArrayList<PhoneEntry>()
        private val seen = HashSet<String>()

        fun add(e: PhoneEntry) {
            val key = PhoneUtils.matchKey(e.number).ifEmpty { e.number }
            if (seen.add(key)) numbers.add(e)
        }

        fun build() = Contact(id, lookupKey, name, photoUri, thumbUri, starred, numbers)
    }

    /** Every contact that has at least one phone number, sorted by name. Empty when permission is missing. */
    fun loadAll(): List<Contact> {
        if (!hasReadPermission()) return emptyList()
        val projection = arrayOf(
            Phone.CONTACT_ID,
            Phone.LOOKUP_KEY,
            Phone.DISPLAY_NAME_PRIMARY,
            Phone.PHOTO_URI,
            Phone.PHOTO_THUMBNAIL_URI,
            Phone.STARRED,
            Phone.NUMBER,
            Phone.TYPE,
            Phone.LABEL,
            Phone.IS_PRIMARY,
        )
        val builders = LinkedHashMap<Long, Builder>()
        try {
            context.contentResolver.query(Phone.CONTENT_URI, projection, null, null, null)?.use { c ->
                val iId = c.getColumnIndexOrThrow(Phone.CONTACT_ID)
                val iLookup = c.getColumnIndexOrThrow(Phone.LOOKUP_KEY)
                val iName = c.getColumnIndexOrThrow(Phone.DISPLAY_NAME_PRIMARY)
                val iPhoto = c.getColumnIndexOrThrow(Phone.PHOTO_URI)
                val iThumb = c.getColumnIndexOrThrow(Phone.PHOTO_THUMBNAIL_URI)
                val iStar = c.getColumnIndexOrThrow(Phone.STARRED)
                val iNumber = c.getColumnIndexOrThrow(Phone.NUMBER)
                val iType = c.getColumnIndexOrThrow(Phone.TYPE)
                val iLabel = c.getColumnIndexOrThrow(Phone.LABEL)
                val iPrimary = c.getColumnIndexOrThrow(Phone.IS_PRIMARY)
                while (c.moveToNext()) {
                    val number = c.getString(iNumber)?.trim().orEmpty()
                    if (number.isEmpty()) continue
                    val id = c.getLong(iId)
                    val b = builders.getOrPut(id) {
                        val name = c.getString(iName)?.trim().orEmpty().ifEmpty { number }
                        Builder(id, c.getString(iLookup), name, c.getString(iPhoto), c.getString(iThumb), c.getInt(iStar) == 1)
                    }
                    val typeLabel = try {
                        Phone.getTypeLabel(context.resources, c.getInt(iType), c.getString(iLabel)).toString()
                    } catch (e: Exception) {
                        ""
                    }
                    b.add(PhoneEntry(number, typeLabel, c.getInt(iPrimary) != 0))
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        } catch (e: RuntimeException) {
            return emptyList()
        }
        val collator = Collator.getInstance()
        return builders.values.map { it.build() }.sortedWith { a, b -> collator.compare(a.name, b.name) }
    }

    /** Stars / un-stars a contact (Android's own "favorite" flag). Needs WRITE_CONTACTS. */
    fun setStarred(contactId: Long, starred: Boolean): Boolean {
        return try {
            val values = ContentValues().apply { put(ContactsContract.Contacts.STARRED, if (starred) 1 else 0) }
            val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
            context.contentResolver.update(uri, values, null, null) > 0
        } catch (e: SecurityException) {
            false
        } catch (e: RuntimeException) {
            false
        }
    }
}
