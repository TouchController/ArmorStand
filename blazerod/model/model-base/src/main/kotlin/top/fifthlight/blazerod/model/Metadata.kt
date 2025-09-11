package top.fifthlight.blazerod.model

data class Metadata(
    val title: String? = null,
    // For PMX
    val titleUniversal: String? = null,
    val comment: String? = null,
    val commentUniversal: String? = null,

    val version: String? = null,
    val authors: List<Author>? = null,
    val copyrightInformation: String? = null,
    val contactInformation: String? = null,
    val references: List<String>? = null,
    val thirdPartyLicenses: String? = null,
    val thumbnail: Texture? = null,

    // License information
    val licenseType: String? = null,
    val licenseUrl: String? = null,
    val licenseDescription: String? = null,
    val specLicenseUrl: String? = null,

    // Usage permissions
    val allowedUser: AllowedUser? = null,
    val allowViolentUsage: Boolean? = null,
    val allowSexualUsage: Boolean? = null,
    val commercialUsage: CommercialUsage? = null,
    val allowPoliticalOrReligiousUsage: Boolean? = null,
    val allowAntisocialOrHateUsage: Boolean? = null,
    val creditNotation: CreditNotation? = null,
    val allowRedistribution: Boolean? = null,
    val modificationPermission: ModificationPermission? = null,
    val permissionUrl: String? = null,

    // For bedrock model
    val linkHome: String? = null,
    val linkDonate: String? = null,
) {
    data class Author(
        val name: String,
        val role: String? = null,
        val contact: List<Pair<String, String>>? = null,
        val comment: String? = null,
    )

    enum class AllowedUser {
        ONLY_AUTHOR,
        EXPLICITLY_LICENSED_PERSON,
        EVERYONE
    }

    enum class CommercialUsage {
        DISALLOW,
        ALLOW,
        PERSONAL_NON_PROFIT,
        PERSONAL_PROFIT,
        CORPORATION,
    }

    enum class CreditNotation {
        REQUIRED,
        UNNECESSARY,
    }

    enum class ModificationPermission {
        PROHIBITED,
        ALLOW_MODIFICATION,
        ALLOW_MODIFICATION_REDISTRIBUTION,
    }

    override fun toString(): String = buildString {
        appendLine("Metadata:")

        fun appendLine(content: String?, prefix: String) = content?.let {
            append(prefix)
            append(content)
            appendLine()
        }

        fun <T> appendLine(content: T?, prefix: String, mapper: (T) -> String) = content?.let {
            append(prefix)
            append(mapper(content))
            appendLine()
        }

        // Basic information
        appendLine(title, "Title: ")
        appendLine(titleUniversal, "Universal Title: ")
        appendLine(comment, "Comment: ")
        appendLine(commentUniversal, "Universal Comment: ")
        appendLine(version, "Version: ")

        // Authors and references
        appendLine(authors?.joinToString(", "), "Authors: ")
        appendLine(copyrightInformation, "Copyright: ")
        appendLine(contactInformation, "Contact: ")
        appendLine(references?.joinToString(", "), "References: ")
        appendLine(thirdPartyLicenses, "Third Party Licenses: ")

        // License information
        appendLine(licenseType, "License Type: ")
        appendLine(licenseDescription, "License Description: ")
        appendLine(licenseUrl, "License URL: ")
        appendLine(specLicenseUrl, "Spec License URL: ")

        // Usage permissions
        appendLine("\nUsage Permissions:")
        appendLine(allowedUser, "• Allowed Users: ", AllowedUser::name)
        appendLine(allowViolentUsage, "• Allow Violent Usage: ", Boolean::toString)
        appendLine(allowSexualUsage, "• Allow Sexual Usage: ", Boolean::toString)
        appendLine(commercialUsage, "• Commercial Usage: ", CommercialUsage::name)
        appendLine(allowPoliticalOrReligiousUsage, "• Allow Political/Religious Usage: ", Boolean::toString)
        appendLine(allowAntisocialOrHateUsage, "• Allow Antisocial/Hate Usage: ", Boolean::toString)
        appendLine(creditNotation, "• Credit Notation: ", CreditNotation::name)
        appendLine(allowRedistribution, "• Allow Redistribution: ", Boolean::toString)
        appendLine(modificationPermission, "• Modification Permission: ", ModificationPermission::name)
        appendLine(permissionUrl, "• Permission URL: ")

        appendLine("Links:")
        appendLine(linkHome, "• Home: ")
        appendLine(linkDonate, "• Donate: ")
    }
}
