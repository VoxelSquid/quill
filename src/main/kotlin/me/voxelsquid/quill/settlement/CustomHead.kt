@file:Suppress("DEPRECATION")

package me.voxelsquid.quill.settlement

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.profile.PlayerProfile
import org.bukkit.profile.PlayerTextures
import java.net.URL
import java.util.*

class CustomHead {

    private fun getProfile(url: String): PlayerProfile {
        val profile: PlayerProfile = Bukkit.createPlayerProfile(UUID.randomUUID())
        val textures: PlayerTextures = profile.textures
        val urlObject = URL(url)
        textures.skin = urlObject
        profile.setTextures(textures)
        return profile
    }

    fun base64(texture: String) : ItemStack {
        val profile = getProfile("https://textures.minecraft.net/texture/$texture")
        val head = ItemStack(Material.PLAYER_HEAD)
        val meta = head.itemMeta as SkullMeta
        meta.ownerProfile = profile
        head.setItemMeta(meta)
        return head
    }

}