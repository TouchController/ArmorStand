package top.fifthlight.blazerod.model.gltf.format.extension

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.joml.Vector3f
import top.fifthlight.blazerod.model.Metadata
import top.fifthlight.blazerod.model.Texture

@Serializable
internal data class VrmV0Extension(
    val exporterVersion: String? = null,
    val meta: Meta? = null,
    val firstPerson: FirstPerson? = null,
    val humanoid: Humanoid? = null,
    val blendShapeMaster: BlendShapeMaster? = null,
) {
    @Serializable
    data class Meta(
        val title: String? = null,
        val author: String? = null,
        val allowedUserName: AllowedUserName? = null,
        val violentUssageName: UsageIdentifier? = null,
        val sexualUssageName: UsageIdentifier? = null,
        val commercialUssageName: UsageIdentifier? = null,
        val licenseName: String? = null,
        val version: String? = null,
        val contactInformation: String? = null,
        val reference: String? = null,
        val texture: Int? = null,
        val otherPermissionUrl: String? = null,
        val otherLicenseUrl: String? = null
    ) {
        fun toMetadata(thumbnailGetter: (Int) -> Texture?) = Metadata(
            title = title,
            authors = author?.let { listOf(Metadata.Author(it)) },
            allowedUser = when (allowedUserName) {
                AllowedUserName.ONLY_AUTHOR -> Metadata.AllowedUser.ONLY_AUTHOR
                AllowedUserName.EXPLICITLY_LICENSED_PERSON -> Metadata.AllowedUser.EXPLICITLY_LICENSED_PERSON
                AllowedUserName.EVERYONE -> Metadata.AllowedUser.EVERYONE
                null -> null
            },
            allowViolentUsage = violentUssageName?.usable,
            allowSexualUsage = sexualUssageName?.usable,
            commercialUsage = when (commercialUssageName) {
                null -> null
                UsageIdentifier.ALLOW -> Metadata.CommercialUsage.ALLOW
                UsageIdentifier.DISALLOW -> Metadata.CommercialUsage.DISALLOW
            },
            licenseType = licenseName,
            version = version,
            contactInformation = contactInformation,
            references = reference?.let { listOf(it) },
            thumbnail = texture?.let { thumbnailGetter(it) },
            licenseUrl = otherLicenseUrl,
            permissionUrl = otherPermissionUrl,
        )

        @Serializable
        enum class AllowedUserName {
            @SerialName("OnlyAuthor")
            ONLY_AUTHOR,

            @SerialName("ExplicitlyLicensedPerson")
            EXPLICITLY_LICENSED_PERSON,

            @SerialName("Everyone")
            EVERYONE
        }

        @Serializable
        enum class UsageIdentifier(val usable: Boolean) {
            @SerialName("Disallow")
            DISALLOW(false),

            @SerialName("Allow")
            ALLOW(true),
        }
    }

    @Serializable
    data class FirstPersonBoneOffset(
        val x: Float,
        val y: Float,
        val z: Float,
    ) {
        val vector
            get() = Vector3f(x, y, z)
    }

    @Serializable
    data class FirstPerson(
        val firstPersonBone: Int? = null,
        val firstPersonBoneOffset: FirstPersonBoneOffset? = null,
        val meshAnnotations: List<MeshAnnotation>? = null,
        val lookAtTypeName: String? = null,
        val lookAtHorizontalInner: DegreeMap? = null,
        val lookAtHorizontalOuter: DegreeMap? = null,
        val lookAtVerticalDown: DegreeMap? = null,
        val lookAtVerticalUp: DegreeMap? = null
    ) {
        @Serializable
        data class MeshAnnotation(
            val mesh: Int,
            val firstPersonFlag: String
        )

        @Serializable
        data class DegreeMap(
            val curve: List<Float>? = null,
            val xRange: Float? = null,
            val yRange: Float? = null
        )
    }

    @Serializable
    data class Humanoid(
        val humanBones: List<HumanBone>? = null,
    ) {
        @Serializable
        data class HumanBone(
            val bone: String,
            val node: Int,
        )
    }

    @Serializable
    data class BlendShapeMaster(
        val blendShapeGroups: List<Group>? = null,
    ) {
        @Serializable
        data class Group(
            val name: String? = null,
            val presetName: String? = null,
            val binds: List<Bind>? = null,
        ) {
            @Serializable
            data class Bind(
                val mesh: Int,
                val index: Int,
                val weight: Float? = null,
            )
        }
    }
}

@Serializable
data class VrmV1Extension(
    val meta: Meta? = null,
    val humanoid: Humanoid? = null,
    val expressions: Expressions? = null,
) {
    @Serializable
    data class Meta(
        // 保留顺序的键值对
        val name: String? = null,
        val authors: List<String>? = null,
        val licenseUrl: String? = null,
        val version: String? = null,
        val copyrightInformation: String? = null,
        val contactInformation: String? = null,
        val references: List<String>? = null,
        val thirdPartyLicenses: String? = null,
        val thumbnailImage: Int? = null,
        val avatarPermission: AvatarPermission? = null,
        val allowExcessivelyViolentUsage: Boolean? = null,
        val allowExcessivelySexualUsage: Boolean? = null,
        val commercialUsage: CommercialUsage? = null,
        val allowPoliticalOrReligiousUsage: Boolean? = null,
        val allowAntisocialOrHateUsage: Boolean? = null,
        val creditNotation: CreditNotation? = null,
        val allowRedistribution: Boolean? = null,
        val modification: Modification? = null,
        val otherLicenseUrl: String? = null,
    ) {
        fun toMetadata(thumbnailGetter: (Int) -> Texture?) = Metadata(
            title = name,
            authors = authors?.map { Metadata.Author(it) },
            licenseUrl = licenseUrl,
            version = version,
            copyrightInformation = copyrightInformation,
            contactInformation = contactInformation,
            references = references,
            thirdPartyLicenses = thirdPartyLicenses,
            thumbnail = thumbnailImage?.let { thumbnailGetter(it) },
            allowedUser = when (avatarPermission) {
                AvatarPermission.ONLY_AUTHOR -> Metadata.AllowedUser.ONLY_AUTHOR
                AvatarPermission.ONLY_SEPARATELY_LICENSED_PERSON -> Metadata.AllowedUser.EXPLICITLY_LICENSED_PERSON
                AvatarPermission.EVERYONE -> Metadata.AllowedUser.EVERYONE
                null -> null
            },
            allowViolentUsage = allowExcessivelyViolentUsage,
            allowSexualUsage = allowExcessivelySexualUsage,
            commercialUsage = when (commercialUsage) {
                CommercialUsage.PERSONAL_NON_PROFIT -> Metadata.CommercialUsage.PERSONAL_NON_PROFIT
                CommercialUsage.PERSONAL_PROFIT -> Metadata.CommercialUsage.PERSONAL_PROFIT
                CommercialUsage.CORPORATION -> Metadata.CommercialUsage.CORPORATION
                null -> null
            },
            allowPoliticalOrReligiousUsage = allowPoliticalOrReligiousUsage,
            allowAntisocialOrHateUsage = allowAntisocialOrHateUsage,
            creditNotation = when (creditNotation) {
                CreditNotation.REQUIRED -> Metadata.CreditNotation.REQUIRED
                CreditNotation.UNNECESSARY -> Metadata.CreditNotation.UNNECESSARY
                null -> null
            },
            allowRedistribution = allowRedistribution,
            modificationPermission = when (modification) {
                Modification.PROHIBITED -> Metadata.ModificationPermission.PROHIBITED
                Modification.ALLOW_MODIFICATION -> Metadata.ModificationPermission.ALLOW_MODIFICATION
                Modification.ALLOW_MODIFICATION_REDISTRIBUTION -> Metadata.ModificationPermission.ALLOW_MODIFICATION_REDISTRIBUTION
                null -> null
            },
            specLicenseUrl = licenseUrl,
        )

        @Serializable
        enum class AvatarPermission {
            @SerialName("onlyAuthor")
            ONLY_AUTHOR,

            @SerialName("onlySeparatelyLicensedPerson")
            ONLY_SEPARATELY_LICENSED_PERSON,

            @SerialName("everyone")
            EVERYONE,
        }

        @Serializable
        enum class CommercialUsage {
            @SerialName("personalNonProfit")
            PERSONAL_NON_PROFIT,

            @SerialName("personalProfit")
            PERSONAL_PROFIT,

            @SerialName("corporation")
            CORPORATION,
        }

        @Serializable
        enum class CreditNotation {
            @SerialName("required")
            REQUIRED,

            @SerialName("unnecessary")
            UNNECESSARY,
        }

        @Serializable
        enum class Modification {
            @SerialName("prohibited")
            PROHIBITED,

            @SerialName("allowModification")
            ALLOW_MODIFICATION,

            @SerialName("allowModificationRedistribution")
            ALLOW_MODIFICATION_REDISTRIBUTION,
        }
    }

    @Serializable
    data class Humanoid(
        val humanBones: Map<String, HumanBone>? = null,
    ) {
        @Serializable
        data class HumanBone(
            val node: Int,
        )
    }

    @Serializable
    data class Expressions(
        val preset: Map<String, Expression>? = null,
        val custom: Map<String, Expression>? = null,
    )

    @Serializable
    data class Expression(
        val isBinary: Boolean? = false,
        val morphTargetBinds: List<MorphTargetBind>? = null,
    ) {
        @Serializable
        data class MorphTargetBind(
            val node: Int,
            val index: Int,
            val weight: Float? = null,
        )
    }
}
