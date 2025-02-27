const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

/**
 * Callable function to check if a license plate is unique.
 * Expects data: { licensePlate: string }
 * Returns: { isUnique: boolean }
 */
exports.checkLicensePlateUnique = functions.https.onCall(
    async (data, context) => {
      const {licensePlate} = data;

      // Validate input
      if (!licensePlate || typeof licensePlate !== "string") {
        throw new functions.https.HttpsError(
            "invalid-argument",
            "The function must be called with a valid licensePlate.",
        );
      }

      try {
        const driversRef = admin.firestore().collection("drivers");
        const querySnapshot = await driversRef
            .where("licensePlate", "==", licensePlate)
            .limit(1)
            .get();

        const isUnique = querySnapshot.empty;
        return {isUnique};
      } catch (error) {
        console.error(
            "Error checking license plate uniqueness:",
            error,
        );
        throw new functions.https.HttpsError(
            "internal",
            "Error checking license plate uniqueness.",
        );
      }
    },
);
