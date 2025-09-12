package top.fifthlight.blazerod.model

// VRM spec: https://github.com/vrm-c/vrm-specification/blob/master/specification/VRMC_vrm-1.0/humanoid.md#list-of-humanoid-bones
// PMX reference: https://vrstormlab.com/dancexr/features/bones
enum class HumanoidTag(
    val vrmName: String? = null,
    val pmxEnglish: String? = null,
    val pmxJapanese: String? = null,
) {
    // Torso
    HIPS(vrmName = "hips", pmxEnglish = "Center", pmxJapanese = "センター"),
    SPINE(vrmName = "spine", pmxEnglish = "Torso", pmxJapanese = "上半身"),
    CHEST(vrmName = "chest", pmxEnglish = "Torso2", pmxJapanese = "上半身2"),
    UPPER_CHEST(vrmName = "upperChest"),
    NECK(vrmName = "neck", pmxEnglish = "Neck", pmxJapanese = "首"),

    // Head
    HEAD(vrmName = "head", pmxEnglish = "Head", pmxJapanese = "頭"),

    // Disable them because we have no correct VRM gaze control
    // LEFT_EYE(vrmName = "leftEye", pmxEnglish = "LeftEye", pmxJapanese = "左目"),
    // RIGHT_EYE(vrmName = "rightEye", pmxEnglish = "RightEye", pmxJapanese = "右目"),
    JAW(vrmName = "jaw"),

    // Leg
    LEFT_UPPER_LEG(vrmName = "leftUpperLeg", pmxEnglish = "LeftLeg", pmxJapanese = "左足"),
    LEFT_LOWER_LEG(vrmName = "leftLowerLeg", pmxEnglish = "LeftKnee", pmxJapanese = "左ひざ"),
    LEFT_FOOT(vrmName = "leftFoot", pmxEnglish = "LeftAnkle", pmxJapanese = "左足首"),
    LEFT_TOES(vrmName = "leftToes", pmxEnglish = "LeftToe", pmxJapanese = "左つま先"),
    RIGHT_UPPER_LEG(vrmName = "rightUpperLeg", pmxEnglish = "RightLeg", pmxJapanese = "右足"),
    RIGHT_LOWER_LEG(vrmName = "rightLowerLeg", pmxEnglish = "RightKnee", pmxJapanese = "右ひざ"),
    RIGHT_FOOT(vrmName = "rightFoot", pmxEnglish = "RightAnkle", pmxJapanese = "右足首"),
    RIGHT_TOES(vrmName = "rightToes", pmxEnglish = "RightToe", pmxJapanese = "右つま先"),

    // Arm
    LEFT_SHOULDER(vrmName = "leftShoulder", pmxEnglish = "LeftShoulder", pmxJapanese = "左肩"),
    LEFT_UPPER_ARM(vrmName = "leftUpperArm", pmxEnglish = "LeftArm", pmxJapanese = "左腕"),
    LEFT_LOWER_ARM(vrmName = "leftLowerArm", pmxEnglish = "LeftElbow", pmxJapanese = "左ひじ"),
    LEFT_HAND(vrmName = "leftHand", pmxEnglish = "LeftWrist", pmxJapanese = "左手首"),
    RIGHT_SHOULDER(vrmName = "rightShoulder", pmxEnglish = "RightShoulder", pmxJapanese = "右肩"),
    RIGHT_UPPER_ARM(vrmName = "rightUpperArm", pmxEnglish = "RightArm", pmxJapanese = "右腕"),
    RIGHT_LOWER_ARM(vrmName = "rightLowerArm", pmxEnglish = "RightElbow", pmxJapanese = "右ひじ"),
    RIGHT_HAND(vrmName = "rightHand", pmxEnglish = "RightWrist", pmxJapanese = "右手首"),

    // Finger
    LEFT_THUMB_METACARPAL(vrmName = "leftThumbMetacarpal", pmxEnglish = "LeftThumb1", pmxJapanese = "左親指１"),
    LEFT_THUMB_PROXIMAL(vrmName = "leftThumbProximal", pmxEnglish = "LeftThumb2", pmxJapanese = "左親指２"),
    LEFT_THUMB_DISTAL(vrmName = "leftThumbDistal", pmxEnglish = "LeftThumb3", pmxJapanese = "左親指３"),
    LEFT_INDEX_PROXIMAL(vrmName = "leftIndexProximal", pmxEnglish = "LeftIndexFinger1", pmxJapanese = "左人指１"),
    LEFT_INDEX_INTERMEDIATE(
        vrmName = "leftIndexIntermediate",
        pmxEnglish = "LeftIndexFinger2",
        pmxJapanese = "左人指２"
    ),
    LEFT_INDEX_DISTAL(vrmName = "leftIndexDistal", pmxEnglish = "LeftIndexFinger3", pmxJapanese = "左人指３"),
    LEFT_MIDDLE_PROXIMAL(vrmName = "leftMiddleProximal", pmxEnglish = "LeftMiddleFinger1", pmxJapanese = "左中指１"),
    LEFT_MIDDLE_INTERMEDIATE(
        vrmName = "leftMiddleIntermediate",
        pmxEnglish = "LeftMiddleFinger2",
        pmxJapanese = "左中指２"
    ),
    LEFT_MIDDLE_DISTAL(vrmName = "leftMiddleDistal", pmxEnglish = "LeftMiddleFinger3", pmxJapanese = "左中指３"),
    LEFT_RING_PROXIMAL(vrmName = "leftRingProximal", pmxEnglish = "LeftRingFinger1", pmxJapanese = "左薬指１"),
    LEFT_RING_INTERMEDIATE(vrmName = "leftRingIntermediate", pmxEnglish = "LeftRingFinger2", pmxJapanese = "左薬指２"),
    LEFT_RING_DISTAL(vrmName = "leftRingDistal", pmxEnglish = "LeftRingFinger3", pmxJapanese = "左薬指３"),
    LEFT_LITTLE_PROXIMAL(vrmName = "leftLittleProximal", pmxEnglish = "LeftPinky1", pmxJapanese = "左小指１"),
    LEFT_LITTLE_INTERMEDIATE(vrmName = "leftLittleIntermediate", pmxEnglish = "LeftPinky2", pmxJapanese = "左小指２"),
    LEFT_LITTLE_DISTAL(vrmName = "leftLittleDistal", pmxEnglish = "LeftPinky3", pmxJapanese = "左小指３"),
    RIGHT_THUMB_METACARPAL(vrmName = "rightThumbMetacarpal", pmxEnglish = "RightThumb1", pmxJapanese = "右親指１"),
    RIGHT_THUMB_PROXIMAL(vrmName = "rightThumbProximal", pmxEnglish = "RightThumb2", pmxJapanese = "右親指２"),
    RIGHT_THUMB_DISTAL(vrmName = "rightThumbDistal", pmxEnglish = "RightThumb3", pmxJapanese = "右親指３"),
    RIGHT_INDEX_PROXIMAL(vrmName = "rightIndexProximal", pmxEnglish = "RightIndexFinger1", pmxJapanese = "右人指１"),
    RIGHT_INDEX_INTERMEDIATE(
        vrmName = "rightIndexIntermediate",
        pmxEnglish = "RightIndexFinger2",
        pmxJapanese = "右人指２"
    ),
    RIGHT_INDEX_DISTAL(vrmName = "rightIndexDistal", pmxEnglish = "RightIndexFinger3", pmxJapanese = "右人指３"),
    RIGHT_MIDDLE_PROXIMAL(vrmName = "rightMiddleProximal", pmxEnglish = "RightMiddleFinger1", pmxJapanese = "右中指１"),
    RIGHT_MIDDLE_INTERMEDIATE(
        vrmName = "rightMiddleIntermediate",
        pmxEnglish = "RightMiddleFinger2",
        pmxJapanese = "右中指２"
    ),
    RIGHT_MIDDLE_DISTAL(vrmName = "rightMiddleDistal", pmxEnglish = "RightMiddleFinger3", pmxJapanese = "右中指３"),
    RIGHT_RING_PROXIMAL(vrmName = "rightRingProximal", pmxEnglish = "RightRingFinger1", pmxJapanese = "右薬指１"),
    RIGHT_RING_INTERMEDIATE(
        vrmName = "rightRingIntermediate",
        pmxEnglish = "RightRingFinger2",
        pmxJapanese = "右薬指２"
    ),
    RIGHT_RING_DISTAL(vrmName = "rightRingDistal", pmxEnglish = "RightRingFinger3", pmxJapanese = "右薬指３"),
    RIGHT_LITTLE_PROXIMAL(vrmName = "rightLittleProximal", pmxEnglish = "RightPinky1", pmxJapanese = "右小指１"),
    RIGHT_LITTLE_INTERMEDIATE(vrmName = "rightLittleIntermediate", pmxEnglish = "RightPinky2", pmxJapanese = "右小指２"),
    RIGHT_LITTLE_DISTAL(vrmName = "rightLittleDistal", pmxEnglish = "RightPinky3", pmxJapanese = "右小指３");

    companion object {
        private val vrmNameMap = entries.associateBy { it.vrmName?.lowercase() }
        private val pmxEnglishMap = entries.associateBy { it.pmxEnglish?.lowercase() }
        private val pmxJapaneseMap = entries.associateBy { it.pmxJapanese?.lowercase() }

        fun fromVrmName(name: String): HumanoidTag? = vrmNameMap[name.lowercase()]
        fun fromPmxEnglish(name: String): HumanoidTag? = pmxEnglishMap[name.lowercase()]
        fun fromPmxJapanese(name: String): HumanoidTag? = pmxJapaneseMap[name.lowercase()]
    }
}
