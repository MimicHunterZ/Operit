#pragma once

#include <jni.h>

#include <memory>
#include <mutex>
#include <string>
#include <vector>

#if defined(OPERIT_HAS_SABA) && OPERIT_HAS_SABA
#include "Saba/Model/MMD/MMDMaterial.h"
#include "Saba/Model/MMD/MMDModel.h"
#include "glm/glm.hpp"

namespace operit::mmdpreview {

constexpr size_t kPreviewVertexStride = 10;
constexpr int kPreviewMaterialSegmentStride = 7;
constexpr int kTextureFlagHasAlpha = 1;

struct PreviewSceneData {
    std::vector<float> vertices;
    std::vector<int32_t> materialSegments;
    std::vector<std::string> texturePaths;
    std::vector<uint32_t> vertexIndices;
    std::vector<float> addUv1Values;
    std::vector<saba::MMDMaterial> staticMaterials;
};

bool GetPreviewSceneDataCached(const std::string& modelPath, PreviewSceneData* outData, std::string* outError);
const PreviewSceneData* LockPreviewSceneData(const std::string& modelPath, std::unique_lock<std::mutex>* outLock, std::string* outError);

bool CopyCurrentAnimatedVertices(
    const saba::MMDModel& model,
    const std::vector<uint32_t>& previewVertexIndices,
    const std::vector<float>& addUv1Values,
    std::vector<float>* animatedVertices,
    std::string* outError
);

bool RenderPreviewScene(
    JNIEnv* env,
    jobject materialSegmentsBuffer,
    jobject textureIdsBySlotBuffer,
    jobject textureFlagsBySlotBuffer,
    jintArray mainProgramHandles,
    jintArray edgeProgramHandles,
    const float* drawVertexData,
    int vertexCount,
    const glm::mat4& modelMat,
    const glm::mat4& viewMat,
    const glm::mat4& projectionMat,
    const saba::MMDMaterial* materials,
    size_t materialCount,
    float fitScale,
    float cameraDistance,
    std::string* outError
);

}
#endif
