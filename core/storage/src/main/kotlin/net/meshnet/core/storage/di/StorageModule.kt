package net.meshnet.core.storage.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.meshnet.core.crypto.IdentityManager
import net.meshnet.core.storage.MeshDatabase
import net.meshnet.core.storage.dao.MessageDao
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.MessageDigest
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideMeshDatabase(
        @ApplicationContext context: Context,
        identityManager: IdentityManager
    ): MeshDatabase {
        // Derive a 32-byte passphrase for SQLCipher from the Ed25519 Identity Key.
        // This ensures the database is bound to the device's cryptographic identity
        // and provides seamless UX without requiring a PIN.
        val rawIdentityKey = identityManager.getPublicKey()
        
        // Hash it to ensure it's a fixed length, high entropy passphrase
        val digest = MessageDigest.getInstance("SHA-256")
        val passphraseBytes = digest.digest(rawIdentityKey)
        
        // SQLCipher requires a SupportOpenHelperFactory
        val factory = SupportOpenHelperFactory(passphraseBytes)

        return Room.databaseBuilder(
            context,
            MeshDatabase::class.java,
            MeshDatabase.DATABASE_NAME
        )
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration() // For development only
            .build()
    }

    @Provides
    @Singleton
    fun provideMessageDao(database: MeshDatabase): MessageDao {
        return database.messageDao()
    }
}
