package phucuong.ht.modules.media.storage;

import com.cloudinary.Cloudinary;
import org.junit.jupiter.api.Test;
import phucuong.ht.modules.media.config.MediaProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Kiểm tra phần không cần mạng của provider Cloudinary: ánh xạ khóa → public_id và URL đã ký (type=authenticated). */
class CloudinaryStorageProviderTest {

    private static final String URL = "cloudinary://123456789012345:test-secret@demo-cloud";

    @Test
    void mapsLogicalKeysToPublicIdAndFormat() {
        String key = "ht/prod/dioceses/phu-cuong-1a2b3c4d/avatars/abc/thumb.jpg";
        assertEquals("ht/prod/dioceses/phu-cuong-1a2b3c4d/avatars/abc/thumb", CloudinaryStorageProvider.publicId(key));
        assertEquals("jpg", CloudinaryStorageProvider.extension(key));
        // Khóa không có đuôi tệp (hoặc dấu chấm nằm trong tên thư mục) vẫn nguyên vẹn.
        assertEquals("ht/v1.2/avatars/abc", CloudinaryStorageProvider.publicId("ht/v1.2/avatars/abc"));
        assertEquals("jpg", CloudinaryStorageProvider.extension("ht/v1.2/avatars/abc"));
    }

    @Test
    void buildsSignedAuthenticatedDeliveryUrlsThatAreNotPublic() {
        MediaProperties properties = new MediaProperties();
        properties.getCloudinary().setUrl(URL);
        CloudinaryStorageProvider provider = new CloudinaryStorageProvider(properties);
        assertEquals("cloudinary", provider.name());

        Cloudinary cloudinary = new Cloudinary(URL);
        cloudinary.config.secure = true;
        String url = cloudinary.url().resourceType("image").type("authenticated").signed(true).format("jpg")
                .generate("ht/prod/system/avatars/abc/thumb");
        assertTrue(url.startsWith("https://res.cloudinary.com/demo-cloud/image/authenticated/s--"), url);
        assertTrue(url.contains("/ht/prod/system/avatars/abc/thumb.jpg"), url);
    }

    @org.junit.jupiter.api.Test
    void folderIsTheKeyDirectory() {
        assertEquals("ht/prod/dioceses/phu-cuong-1a2b3c4d/avatars/abc", CloudinaryStorageProvider.folderOf("ht/prod/dioceses/phu-cuong-1a2b3c4d/avatars/abc/thumb.jpg"));
        assertEquals(null, CloudinaryStorageProvider.folderOf("thumb.jpg"));
    }
}
