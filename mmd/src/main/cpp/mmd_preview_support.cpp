#include "mmd_preview_support.h"

#if defined(OPERIT_HAS_SABA) && OPERIT_HAS_SABA

#include <algorithm>
#include <cctype>
#include <cmath>
#include <filesystem>
#include <limits>
#include <mutex>
#include <sstream>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

#include <GLES2/gl2.h>

#include "Saba/Model/MMD/PMDFile.h"
#include "Saba/Model/MMD/PMXFile.h"
#include "glm/gtc/type_ptr.hpp"

namespace operit::mmdpreview {
namespace {

namespace fs = std::filesystem;

constexpr const char* kBuiltinAssetPrefix = "@mmd_builtin/";
constexpr size_t kMaxPreviewTriangles = 500000;

enum class PreviewSphereMode : int32_t {
    None = 0,
    Mul = 1,
    Add = 2,
    SubTexture = 3,
};

struct TextureSearchIndex {
    std::string rootDir;
    std::unordered_map<std::string, std::string> exactRelativePathMap;
    std::unordered_map<std::string, std::vector<std::string>> filenameMap;
    std::unordered_map<std::string, std::vector<std::string>> basenameMap;
};

struct PreviewSceneCache {
    std::string modelPath;
    PreviewSceneData data;
};

std::mutex gPreviewSceneCacheMutex;
PreviewSceneCache gPreviewSceneCache;

std::string ToLowerAscii(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char ch) {
        return static_cast<char>(std::tolower(ch));
    });
    return value;
}

std::string TrimAscii(std::string value) {
    value.erase(value.begin(), std::find_if(value.begin(), value.end(), [](unsigned char ch) {
        return !std::isspace(ch);
    }));
    value.erase(std::find_if(value.rbegin(), value.rend(), [](unsigned char ch) {
        return !std::isspace(ch);
    }).base(), value.end());
    return value;
}

std::string NormalizePathSeparators(std::string path) {
    std::replace(path.begin(), path.end(), '\\', '/');
    return path;
}

bool IsAbsolutePath(const std::string& path) {
    if (path.empty()) {
        return false;
    }
    if (path[0] == '/' || path[0] == '\\') {
        return true;
    }
    return path.size() > 1 && path[1] == ':';
}

std::string JoinPaths(const std::string& base, const std::string& relative) {
    if (base.empty()) {
        return relative;
    }
    if (relative.empty()) {
        return base;
    }
    if (base.back() == '/' || base.back() == '\\') {
        return base + relative;
    }
    return base + "/" + relative;
}

std::string NormalizeLexicalPath(const std::string& path) {
    if (path.empty()) {
        return "";
    }
    std::error_code ec;
    fs::path fsPath(path);
    const auto normalized = fsPath.lexically_normal();
    (void) ec;
    return NormalizePathSeparators(normalized.string());
}

std::string GetFileExtension(const std::string& path) {
    const auto dotPosition = path.find_last_of('.');
    if (dotPosition == std::string::npos) {
        return "";
    }
    return ToLowerAscii(path.substr(dotPosition + 1));
}

std::string GetParentDirectory(const std::string& path) {
    const auto slashPosition = path.find_last_of("/\\");
    if (slashPosition == std::string::npos) {
        return "";
    }
    return path.substr(0, slashPosition);
}

bool IsRegularFile(const std::string& path) {
    std::error_code ec;
    return fs::is_regular_file(fs::path(path), ec);
}

std::string BuildBuiltinToonToken(int toonNumber) {
    std::ostringstream stream;
    stream << kBuiltinAssetPrefix << "mmd_common_toon/toon";
    if (toonNumber < 10) {
        stream << '0';
    }
    stream << toonNumber << ".bmp";
    return stream.str();
}

std::string MaybeResolveBuiltinToonToken(const std::string& textureName) {
    const std::string filenameLower = ToLowerAscii(fs::path(textureName).filename().string());
    if (filenameLower.size() != 10 || filenameLower.rfind("toon", 0) != 0 || filenameLower.substr(6) != ".bmp") {
        return "";
    }
    const std::string indexPart = filenameLower.substr(4, 2);
    if (!std::isdigit(indexPart[0]) || !std::isdigit(indexPart[1])) {
        return "";
    }
    const int toonIndex = (indexPart[0] - '0') * 10 + (indexPart[1] - '0');
    if (toonIndex < 1 || toonIndex > 10) {
        return "";
    }
    return BuildBuiltinToonToken(toonIndex);
}

TextureSearchIndex BuildTextureSearchIndex(const std::string& rootDir) {
    TextureSearchIndex index;
    index.rootDir = NormalizeLexicalPath(rootDir);
    if (index.rootDir.empty()) {
        return index;
    }

    std::error_code ec;
    const fs::path rootPath(index.rootDir);
    if (!fs::exists(rootPath, ec) || !fs::is_directory(rootPath, ec)) {
        return index;
    }

    fs::recursive_directory_iterator iterator(rootPath, fs::directory_options::skip_permission_denied, ec);
    const fs::recursive_directory_iterator end;
    while (!ec && iterator != end) {
        const auto entry = *iterator;
        iterator.increment(ec);
        if (ec) {
            ec.clear();
            continue;
        }

        std::error_code entryEc;
        if (!entry.is_regular_file(entryEc) || entryEc) {
            continue;
        }

        const std::string absolutePath = NormalizePathSeparators(entry.path().lexically_normal().string());
        std::error_code relativeEc;
        const std::string relativePath = NormalizePathSeparators(fs::relative(entry.path(), rootPath, relativeEc).string());
        if (!relativeEc && !relativePath.empty()) {
            index.exactRelativePathMap[ToLowerAscii(relativePath)] = absolutePath;
        }

        const std::string filenameLower = ToLowerAscii(entry.path().filename().string());
        const std::string basenameLower = ToLowerAscii(entry.path().stem().string());
        index.filenameMap[filenameLower].push_back(absolutePath);
        index.basenameMap[basenameLower].push_back(absolutePath);
    }

    return index;
}

std::string PickBestCandidate(
    const TextureSearchIndex& index,
    const std::vector<std::string>& candidates,
    const fs::path& wantedPath,
    const std::string& wantedFilenameLower,
    const std::string& wantedBasenameLower,
    const std::string& wantedExtensionLower
) {
    int bestScore = std::numeric_limits<int>::min();
    std::string bestPath;

    std::string wantedParentLower = ToLowerAscii(NormalizePathSeparators(wantedPath.parent_path().string()));
    if (wantedParentLower == ".") {
        wantedParentLower.clear();
    }

    for (const auto& candidate : candidates) {
        int score = 0;
        const fs::path candidatePath(candidate);
        const std::string candidateFilenameLower = ToLowerAscii(candidatePath.filename().string());
        const std::string candidateBasenameLower = ToLowerAscii(candidatePath.stem().string());
        const std::string candidateExtensionLower = GetFileExtension(candidate);

        if (candidateFilenameLower == wantedFilenameLower) {
            score += 8;
        }
        if (candidateBasenameLower == wantedBasenameLower) {
            score += 4;
        }
        if (!wantedExtensionLower.empty() && candidateExtensionLower == wantedExtensionLower) {
            score += 2;
        }

        std::error_code ec;
        const std::string relativeParentLower = ToLowerAscii(
            NormalizePathSeparators(fs::relative(candidatePath.parent_path(), fs::path(index.rootDir), ec).string())
        );
        if (!ec && relativeParentLower == wantedParentLower) {
            score += 6;
        }

        if (score > bestScore) {
            bestScore = score;
            bestPath = candidate;
        }
    }

    return bestPath;
}

std::string ResolveTexturePathInternal(
    const std::string& baseDir,
    const TextureSearchIndex& searchIndex,
    const std::string& textureNameRaw
) {
    std::string textureName = NormalizePathSeparators(TrimAscii(textureNameRaw));
    if (textureName.empty()) {
        return "";
    }

    if (textureName.rfind(kBuiltinAssetPrefix, 0) == 0) {
        return textureName;
    }

    const fs::path wantedPath(textureName);
    const std::string exactCandidate = NormalizeLexicalPath(
        IsAbsolutePath(textureName) ? textureName : JoinPaths(baseDir, textureName)
    );
    if (!exactCandidate.empty() && IsRegularFile(exactCandidate)) {
        return exactCandidate;
    }

    const std::string relativeLookup = ToLowerAscii(NormalizePathSeparators(wantedPath.lexically_normal().string()));
    const auto exactMatch = searchIndex.exactRelativePathMap.find(relativeLookup);
    if (exactMatch != searchIndex.exactRelativePathMap.end()) {
        return exactMatch->second;
    }

    const std::string filenameLower = ToLowerAscii(wantedPath.filename().string());
    const std::string basenameLower = ToLowerAscii(wantedPath.stem().string());
    const std::string extensionLower = GetFileExtension(textureName);

    const auto filenameCandidates = searchIndex.filenameMap.find(filenameLower);
    if (filenameCandidates != searchIndex.filenameMap.end()) {
        const auto chosen = PickBestCandidate(searchIndex, filenameCandidates->second, wantedPath, filenameLower, basenameLower, extensionLower);
        if (!chosen.empty()) {
            return chosen;
        }
    }

    const auto basenameCandidates = searchIndex.basenameMap.find(basenameLower);
    if (basenameCandidates != searchIndex.basenameMap.end()) {
        const auto chosen = PickBestCandidate(searchIndex, basenameCandidates->second, wantedPath, filenameLower, basenameLower, extensionLower);
        if (!chosen.empty()) {
            return chosen;
        }
    }

    return "";
}

std::string ResolveToonTexturePath(
    const std::string& baseDir,
    const TextureSearchIndex& searchIndex,
    const std::string& textureNameRaw
) {
    const std::string resolvedPath = ResolveTexturePathInternal(baseDir, searchIndex, textureNameRaw);
    if (!resolvedPath.empty()) {
        return resolvedPath;
    }
    return MaybeResolveBuiltinToonToken(textureNameRaw);
}

void AppendPreviewVertex(
    std::vector<float>* outVertices,
    const glm::vec3& position,
    const glm::vec3& normal,
    const glm::vec2& uv,
    const glm::vec2& addUv1
) {
    outVertices->push_back(position.x);
    outVertices->push_back(position.y);
    outVertices->push_back(position.z);
    outVertices->push_back(normal.x);
    outVertices->push_back(normal.y);
    outVertices->push_back(normal.z);
    outVertices->push_back(uv.x);
    outVertices->push_back(uv.y);
    outVertices->push_back(addUv1.x);
    outVertices->push_back(addUv1.y);
}

int GetOrCreateTextureSlot(
    const std::string& texturePath,
    PreviewSceneData* outData,
    std::unordered_map<std::string, int>* slotMap
) {
    if (texturePath.empty() || outData == nullptr || slotMap == nullptr) {
        return -1;
    }

    const auto found = slotMap->find(texturePath);
    if (found != slotMap->end()) {
        return found->second;
    }

    const int newSlot = static_cast<int>(outData->texturePaths.size());
    outData->texturePaths.push_back(texturePath);
    (*slotMap)[texturePath] = newSlot;
    return newSlot;
}

void AppendMaterialSegment(
    std::vector<int32_t>* outSegments,
    int materialIndex,
    size_t startVertex,
    size_t vertexCount,
    int baseTextureSlot,
    int sphereTextureSlot,
    int toonTextureSlot,
    PreviewSphereMode sphereMode
) {
    outSegments->push_back(static_cast<int32_t>(materialIndex));
    outSegments->push_back(static_cast<int32_t>(startVertex));
    outSegments->push_back(static_cast<int32_t>(vertexCount));
    outSegments->push_back(static_cast<int32_t>(baseTextureSlot));
    outSegments->push_back(static_cast<int32_t>(sphereTextureSlot));
    outSegments->push_back(static_cast<int32_t>(toonTextureSlot));
    outSegments->push_back(static_cast<int32_t>(sphereMode));
}

bool BuildPreviewSceneFromPmd(const std::string& modelPath, PreviewSceneData* outData, std::string* outError) {
    saba::PMDFile pmdFile;
    if (!saba::ReadPMDFile(&pmdFile, modelPath.c_str())) {
        if (outError != nullptr) {
            *outError = "failed to parse PMD file: " + modelPath;
        }
        return false;
    }

    if (pmdFile.m_faces.size() > kMaxPreviewTriangles) {
        if (outError != nullptr) {
            *outError = "model is too complex for preview renderer (PMD face count limit exceeded).";
        }
        return false;
    }

    const std::string baseDir = GetParentDirectory(NormalizePathSeparators(modelPath));
    const TextureSearchIndex textureSearchIndex = BuildTextureSearchIndex(baseDir);

    outData->vertices.clear();
    outData->materialSegments.clear();
    outData->texturePaths.clear();
    outData->vertexIndices.clear();
    outData->addUv1Values.clear();
    outData->staticMaterials.clear();

    outData->vertices.reserve(pmdFile.m_faces.size() * 3 * kPreviewVertexStride);
    outData->vertexIndices.reserve(pmdFile.m_faces.size() * 3);
    outData->addUv1Values.reserve(pmdFile.m_faces.size() * 3 * 2);

    std::vector<std::string> toonTexturePaths;
    toonTexturePaths.reserve(pmdFile.m_toonTextureNames.size());
    for (const auto& toonName : pmdFile.m_toonTextureNames) {
        toonTexturePaths.push_back(ResolveToonTexturePath(baseDir, textureSearchIndex, toonName.ToUtf8String()));
    }

    std::unordered_map<std::string, int> textureSlotMap;
    size_t faceCursor = 0;

    for (const auto& material : pmdFile.m_materials) {
        saba::MMDMaterial sceneMaterial;
        sceneMaterial.m_diffuse = material.m_diffuse;
        sceneMaterial.m_alpha = material.m_alpha;
        sceneMaterial.m_specularPower = material.m_specularPower;
        sceneMaterial.m_specular = material.m_specular;
        sceneMaterial.m_ambient = material.m_ambient;
        sceneMaterial.m_edgeFlag = material.m_edgeFlag;
        sceneMaterial.m_edgeSize = material.m_edgeFlag == 0 ? 0.0f : 1.0f;

        PreviewSphereMode sphereMode = PreviewSphereMode::None;
        std::string textureName = material.m_textureName.ToUtf8String();
        std::string diffuseTextureName;
        std::string sphereTextureName;
        const size_t separatorPosition = textureName.find('*');
        if (separatorPosition == std::string::npos) {
            const std::string extension = GetFileExtension(textureName);
            if (extension == "sph") {
                sphereTextureName = textureName;
                sphereMode = PreviewSphereMode::Mul;
            } else if (extension == "spa") {
                sphereTextureName = textureName;
                sphereMode = PreviewSphereMode::Add;
            } else {
                diffuseTextureName = textureName;
            }
        } else {
            diffuseTextureName = textureName.substr(0, separatorPosition);
            sphereTextureName = textureName.substr(separatorPosition + 1);
            const std::string sphereExtension = GetFileExtension(sphereTextureName);
            if (sphereExtension == "sph") {
                sphereMode = PreviewSphereMode::Mul;
            } else if (sphereExtension == "spa") {
                sphereMode = PreviewSphereMode::Add;
            }
        }

        if (!diffuseTextureName.empty()) {
            sceneMaterial.m_texture = ResolveTexturePathInternal(baseDir, textureSearchIndex, diffuseTextureName);
        }
        if (!sphereTextureName.empty()) {
            sceneMaterial.m_spTexture = ResolveTexturePathInternal(baseDir, textureSearchIndex, sphereTextureName);
            if (sphereMode == PreviewSphereMode::Mul) {
                sceneMaterial.m_spTextureMode = saba::MMDMaterial::SphereTextureMode::Mul;
            } else if (sphereMode == PreviewSphereMode::Add) {
                sceneMaterial.m_spTextureMode = saba::MMDMaterial::SphereTextureMode::Add;
            }
        }
        if (material.m_toonIndex != 255 && material.m_toonIndex < toonTexturePaths.size()) {
            sceneMaterial.m_toonTexture = toonTexturePaths[material.m_toonIndex];
        }

        const int materialIndex = static_cast<int>(outData->staticMaterials.size());
        outData->staticMaterials.push_back(sceneMaterial);
        const int baseTextureSlot = GetOrCreateTextureSlot(sceneMaterial.m_texture, outData, &textureSlotMap);
        const int sphereTextureSlot = GetOrCreateTextureSlot(sceneMaterial.m_spTexture, outData, &textureSlotMap);
        const int toonTextureSlot = GetOrCreateTextureSlot(sceneMaterial.m_toonTexture, outData, &textureSlotMap);
        const size_t startVertex = outData->vertices.size() / kPreviewVertexStride;
        const size_t triangleCount = static_cast<size_t>(material.m_faceVertexCount / 3);
        for (size_t triangleIndex = 0; triangleIndex < triangleCount && faceCursor < pmdFile.m_faces.size(); ++triangleIndex, ++faceCursor) {
            const auto& face = pmdFile.m_faces[faceCursor];
            for (int corner = 0; corner < 3; ++corner) {
                const uint16_t vertexIndex = face.m_vertices[corner];
                if (vertexIndex >= pmdFile.m_vertices.size()) {
                    if (outError != nullptr) {
                        *outError = "invalid PMD face index at face " + std::to_string(faceCursor);
                    }
                    return false;
                }

                const auto& vertex = pmdFile.m_vertices[vertexIndex];
                AppendPreviewVertex(&outData->vertices, vertex.m_position, vertex.m_normal, vertex.m_uv, glm::vec2(0.0f));
                outData->vertexIndices.push_back(static_cast<uint32_t>(vertexIndex));
                outData->addUv1Values.push_back(0.0f);
                outData->addUv1Values.push_back(0.0f);
            }
        }

        const size_t endVertex = outData->vertices.size() / kPreviewVertexStride;
        AppendMaterialSegment(&outData->materialSegments, materialIndex, startVertex, endVertex - startVertex, baseTextureSlot, sphereTextureSlot, toonTextureSlot, sphereMode);
    }

    if (faceCursor < pmdFile.m_faces.size()) {
        const int materialIndex = static_cast<int>(outData->staticMaterials.size());
        outData->staticMaterials.emplace_back();
        const size_t startVertex = outData->vertices.size() / kPreviewVertexStride;
        for (; faceCursor < pmdFile.m_faces.size(); ++faceCursor) {
            const auto& face = pmdFile.m_faces[faceCursor];
            for (int corner = 0; corner < 3; ++corner) {
                const uint16_t vertexIndex = face.m_vertices[corner];
                if (vertexIndex >= pmdFile.m_vertices.size()) {
                    if (outError != nullptr) {
                        *outError = "invalid PMD face index at face " + std::to_string(faceCursor);
                    }
                    return false;
                }
                const auto& vertex = pmdFile.m_vertices[vertexIndex];
                AppendPreviewVertex(&outData->vertices, vertex.m_position, vertex.m_normal, vertex.m_uv, glm::vec2(0.0f));
                outData->vertexIndices.push_back(static_cast<uint32_t>(vertexIndex));
                outData->addUv1Values.push_back(0.0f);
                outData->addUv1Values.push_back(0.0f);
            }
        }
        const size_t endVertex = outData->vertices.size() / kPreviewVertexStride;
        AppendMaterialSegment(&outData->materialSegments, materialIndex, startVertex, endVertex - startVertex, -1, -1, -1, PreviewSphereMode::None);
    }

    return true;
}

bool BuildPreviewSceneFromPmx(const std::string& modelPath, PreviewSceneData* outData, std::string* outError) {
    saba::PMXFile pmxFile;
    if (!saba::ReadPMXFile(&pmxFile, modelPath.c_str())) {
        if (outError != nullptr) {
            *outError = "failed to parse PMX file: " + modelPath;
        }
        return false;
    }

    if (pmxFile.m_faces.size() > kMaxPreviewTriangles) {
        if (outError != nullptr) {
            *outError = "model is too complex for preview renderer (PMX face count limit exceeded).";
        }
        return false;
    }

    const std::string baseDir = GetParentDirectory(NormalizePathSeparators(modelPath));
    const TextureSearchIndex textureSearchIndex = BuildTextureSearchIndex(baseDir);

    outData->vertices.clear();
    outData->materialSegments.clear();
    outData->texturePaths.clear();
    outData->vertexIndices.clear();
    outData->addUv1Values.clear();
    outData->staticMaterials.clear();

    outData->vertices.reserve(pmxFile.m_faces.size() * 3 * kPreviewVertexStride);
    outData->vertexIndices.reserve(pmxFile.m_faces.size() * 3);
    outData->addUv1Values.reserve(pmxFile.m_faces.size() * 3 * 2);

    std::vector<std::string> resolvedTexturePaths;
    resolvedTexturePaths.reserve(pmxFile.m_textures.size());
    for (const auto& texture : pmxFile.m_textures) {
        resolvedTexturePaths.push_back(ResolveTexturePathInternal(baseDir, textureSearchIndex, texture.m_textureName));
    }

    std::unordered_map<std::string, int> textureSlotMap;
    size_t faceCursor = 0;
    const bool hasAddUv1 = pmxFile.m_header.m_addUVNum > 0;

    for (const auto& material : pmxFile.m_materials) {
        saba::MMDMaterial sceneMaterial;
        sceneMaterial.m_diffuse = material.m_diffuse;
        sceneMaterial.m_alpha = material.m_diffuse.a;
        sceneMaterial.m_specularPower = material.m_specularPower;
        sceneMaterial.m_specular = material.m_specular;
        sceneMaterial.m_ambient = material.m_ambient;
        sceneMaterial.m_bothFace = !!((uint8_t)material.m_drawMode & (uint8_t)saba::PMXDrawModeFlags::BothFace);
        sceneMaterial.m_edgeFlag = ((uint8_t)material.m_drawMode & (uint8_t)saba::PMXDrawModeFlags::DrawEdge) == 0 ? 0 : 1;
        sceneMaterial.m_groundShadow = !!((uint8_t)material.m_drawMode & (uint8_t)saba::PMXDrawModeFlags::GroundShadow);
        sceneMaterial.m_shadowCaster = !!((uint8_t)material.m_drawMode & (uint8_t)saba::PMXDrawModeFlags::CastSelfShadow);
        sceneMaterial.m_shadowReceiver = !!((uint8_t)material.m_drawMode & (uint8_t)saba::PMXDrawModeFlags::RecieveSelfShadow);
        sceneMaterial.m_edgeSize = material.m_edgeSize;
        sceneMaterial.m_edgeColor = material.m_edgeColor;

        PreviewSphereMode sphereMode = PreviewSphereMode::None;
        if (material.m_textureIndex >= 0 && material.m_textureIndex < static_cast<int32_t>(resolvedTexturePaths.size())) {
            sceneMaterial.m_texture = resolvedTexturePaths[material.m_textureIndex];
        }
        if (material.m_toonMode == saba::PMXToonMode::Common) {
            if (material.m_toonTextureIndex >= 0) {
                sceneMaterial.m_toonTexture = BuildBuiltinToonToken(material.m_toonTextureIndex + 1);
            }
        } else if (material.m_toonTextureIndex >= 0 && material.m_toonTextureIndex < static_cast<int32_t>(resolvedTexturePaths.size())) {
            sceneMaterial.m_toonTexture = resolvedTexturePaths[material.m_toonTextureIndex];
            if (sceneMaterial.m_toonTexture.empty()) {
                sceneMaterial.m_toonTexture = ResolveToonTexturePath(baseDir, textureSearchIndex, pmxFile.m_textures[material.m_toonTextureIndex].m_textureName);
            }
        }
        if (material.m_sphereTextureIndex >= 0 && material.m_sphereTextureIndex < static_cast<int32_t>(resolvedTexturePaths.size())) {
            sceneMaterial.m_spTexture = resolvedTexturePaths[material.m_sphereTextureIndex];
            if (material.m_sphereMode == saba::PMXSphereMode::Mul) {
                sphereMode = PreviewSphereMode::Mul;
                sceneMaterial.m_spTextureMode = saba::MMDMaterial::SphereTextureMode::Mul;
            } else if (material.m_sphereMode == saba::PMXSphereMode::Add) {
                sphereMode = PreviewSphereMode::Add;
                sceneMaterial.m_spTextureMode = saba::MMDMaterial::SphereTextureMode::Add;
            } else if (material.m_sphereMode == saba::PMXSphereMode::SubTexture) {
                sphereMode = PreviewSphereMode::SubTexture;
            }
        }

        const int materialIndex = static_cast<int>(outData->staticMaterials.size());
        outData->staticMaterials.push_back(sceneMaterial);
        const int baseTextureSlot = GetOrCreateTextureSlot(sceneMaterial.m_texture, outData, &textureSlotMap);
        const int sphereTextureSlot = GetOrCreateTextureSlot(sceneMaterial.m_spTexture, outData, &textureSlotMap);
        const int toonTextureSlot = GetOrCreateTextureSlot(sceneMaterial.m_toonTexture, outData, &textureSlotMap);
        const size_t startVertex = outData->vertices.size() / kPreviewVertexStride;
        const size_t triangleCount = static_cast<size_t>(material.m_numFaceVertices / 3);
        for (size_t triangleIndex = 0; triangleIndex < triangleCount && faceCursor < pmxFile.m_faces.size(); ++triangleIndex, ++faceCursor) {
            const auto& face = pmxFile.m_faces[faceCursor];
            for (int corner = 0; corner < 3; ++corner) {
                const uint32_t vertexIndex = face.m_vertices[corner];
                if (vertexIndex >= pmxFile.m_vertices.size()) {
                    if (outError != nullptr) {
                        *outError = "invalid PMX face index at face " + std::to_string(faceCursor);
                    }
                    return false;
                }

                const auto& vertex = pmxFile.m_vertices[vertexIndex];
                const glm::vec2 addUv1 = hasAddUv1 ? glm::vec2(vertex.m_addUV[0].x, vertex.m_addUV[0].y) : glm::vec2(0.0f);
                AppendPreviewVertex(&outData->vertices, vertex.m_position, vertex.m_normal, vertex.m_uv, addUv1);
                outData->vertexIndices.push_back(vertexIndex);
                outData->addUv1Values.push_back(addUv1.x);
                outData->addUv1Values.push_back(addUv1.y);
            }
        }

        const size_t endVertex = outData->vertices.size() / kPreviewVertexStride;
        AppendMaterialSegment(&outData->materialSegments, materialIndex, startVertex, endVertex - startVertex, baseTextureSlot, sphereTextureSlot, toonTextureSlot, sphereMode);
    }

    if (faceCursor < pmxFile.m_faces.size()) {
        const int materialIndex = static_cast<int>(outData->staticMaterials.size());
        outData->staticMaterials.emplace_back();
        const size_t startVertex = outData->vertices.size() / kPreviewVertexStride;
        for (; faceCursor < pmxFile.m_faces.size(); ++faceCursor) {
            const auto& face = pmxFile.m_faces[faceCursor];
            for (int corner = 0; corner < 3; ++corner) {
                const uint32_t vertexIndex = face.m_vertices[corner];
                if (vertexIndex >= pmxFile.m_vertices.size()) {
                    if (outError != nullptr) {
                        *outError = "invalid PMX face index at face " + std::to_string(faceCursor);
                    }
                    return false;
                }
                const auto& vertex = pmxFile.m_vertices[vertexIndex];
                const glm::vec2 addUv1 = hasAddUv1 ? glm::vec2(vertex.m_addUV[0].x, vertex.m_addUV[0].y) : glm::vec2(0.0f);
                AppendPreviewVertex(&outData->vertices, vertex.m_position, vertex.m_normal, vertex.m_uv, addUv1);
                outData->vertexIndices.push_back(vertexIndex);
                outData->addUv1Values.push_back(addUv1.x);
                outData->addUv1Values.push_back(addUv1.y);
            }
        }
        const size_t endVertex = outData->vertices.size() / kPreviewVertexStride;
        AppendMaterialSegment(&outData->materialSegments, materialIndex, startVertex, endVertex - startVertex, -1, -1, -1, PreviewSphereMode::None);
    }

    return true;
}

bool BuildPreviewScene(const std::string& modelPath, PreviewSceneData* outData, std::string* outError) {
    if (outData == nullptr) {
        if (outError != nullptr) {
            *outError = "internal error: preview scene output buffer is null.";
        }
        return false;
    }

    const std::string extension = GetFileExtension(modelPath);
    if (extension == "pmd") {
        if (!BuildPreviewSceneFromPmd(modelPath, outData, outError)) {
            return false;
        }
    } else if (extension == "pmx") {
        if (!BuildPreviewSceneFromPmx(modelPath, outData, outError)) {
            return false;
        }
    } else {
        if (outError != nullptr) {
            *outError = "unsupported model extension; expected .pmd or .pmx.";
        }
        return false;
    }

    if (outData->vertices.empty()) {
        if (outError != nullptr) {
            *outError = "preview mesh is empty.";
        }
        return false;
    }
    if (outData->materialSegments.empty()) {
        if (outError != nullptr) {
            *outError = "preview material segment list is empty.";
        }
        return false;
    }
    if (outData->vertexIndices.size() * kPreviewVertexStride != outData->vertices.size()) {
        if (outError != nullptr) {
            *outError = "preview mesh index mapping size is inconsistent.";
        }
        return false;
    }
    return true;
}

}

bool GetPreviewSceneDataCached(const std::string& modelPath, PreviewSceneData* outData, std::string* outError) {
    if (outData == nullptr) {
        if (outError != nullptr) {
            *outError = "internal error: preview scene output buffer is null.";
        }
        return false;
    }

    {
        std::lock_guard<std::mutex> lock(gPreviewSceneCacheMutex);
        if (gPreviewSceneCache.modelPath == modelPath && !gPreviewSceneCache.data.vertices.empty()) {
            *outData = gPreviewSceneCache.data;
            return true;
        }
    }

    PreviewSceneData newData;
    if (!BuildPreviewScene(modelPath, &newData, outError)) {
        return false;
    }

    {
        std::lock_guard<std::mutex> lock(gPreviewSceneCacheMutex);
        gPreviewSceneCache.modelPath = modelPath;
        gPreviewSceneCache.data = std::move(newData);
        *outData = gPreviewSceneCache.data;
    }
    return true;
}

const PreviewSceneData* LockPreviewSceneData(const std::string& modelPath, std::unique_lock<std::mutex>* outLock, std::string* outError) {
    if (outLock == nullptr) {
        if (outError != nullptr) {
            *outError = "internal error: preview scene lock output is null.";
        }
        return nullptr;
    }

    PreviewSceneData newData;
    {
        std::lock_guard<std::mutex> lock(gPreviewSceneCacheMutex);
        if (gPreviewSceneCache.modelPath != modelPath || gPreviewSceneCache.data.vertices.empty()) {
            if (!BuildPreviewScene(modelPath, &newData, outError)) {
                return nullptr;
            }
        }
    }

    *outLock = std::unique_lock<std::mutex>(gPreviewSceneCacheMutex);
    if (!newData.vertices.empty()) {
        gPreviewSceneCache.modelPath = modelPath;
        gPreviewSceneCache.data = std::move(newData);
    }
    if (gPreviewSceneCache.modelPath != modelPath || gPreviewSceneCache.data.vertices.empty()) {
        if (outError != nullptr) {
            *outError = "failed to access preview scene cache.";
        }
        return nullptr;
    }
    return &gPreviewSceneCache.data;
}

bool CopyCurrentAnimatedVertices(
    const saba::MMDModel& model,
    const std::vector<uint32_t>& previewVertexIndices,
    const std::vector<float>& addUv1Values,
    std::vector<float>* animatedVertices,
    std::string* outError
) {
    if (animatedVertices == nullptr) {
        if (outError != nullptr) {
            *outError = "internal error: animated preview buffer is null.";
        }
        return false;
    }

    const auto* positions = model.GetUpdatePositions();
    const auto* normals = model.GetUpdateNormals();
    const auto* uvs = model.GetUpdateUVs();
    const size_t modelVertexCount = model.GetVertexCount();
    if (positions == nullptr || normals == nullptr || uvs == nullptr || modelVertexCount == 0) {
        if (outError != nullptr) {
            *outError = "animated model has no renderable vertices.";
        }
        return false;
    }

    const size_t expectedFloatCount = previewVertexIndices.size() * kPreviewVertexStride;
    if (animatedVertices->size() != expectedFloatCount) {
        animatedVertices->resize(expectedFloatCount);
    }

    size_t invalidVertexCount = 0;
    for (size_t index = 0; index < previewVertexIndices.size(); ++index) {
        const uint32_t sourceIndex = previewVertexIndices[index];
        if (sourceIndex >= modelVertexCount) {
            if (outError != nullptr) {
                *outError = "animated preview source index out of range.";
            }
            return false;
        }

        const auto& position = positions[sourceIndex];
        const auto& normal = normals[sourceIndex];
        const auto& uv = uvs[sourceIndex];
        const size_t base = index * kPreviewVertexStride;
        if (!std::isfinite(position.x) || !std::isfinite(position.y) || !std::isfinite(position.z) ||
            !std::isfinite(normal.x) || !std::isfinite(normal.y) || !std::isfinite(normal.z) ||
            !std::isfinite(uv.x) || !std::isfinite(uv.y)) {
            invalidVertexCount += 1;
            continue;
        }

        (*animatedVertices)[base] = position.x;
        (*animatedVertices)[base + 1] = position.y;
        (*animatedVertices)[base + 2] = position.z;
        (*animatedVertices)[base + 3] = normal.x;
        (*animatedVertices)[base + 4] = normal.y;
        (*animatedVertices)[base + 5] = normal.z;
        (*animatedVertices)[base + 6] = uv.x;
        (*animatedVertices)[base + 7] = uv.y;
        (*animatedVertices)[base + 8] = index * 2 + 1 < addUv1Values.size() ? addUv1Values[index * 2] : 0.0f;
        (*animatedVertices)[base + 9] = index * 2 + 1 < addUv1Values.size() ? addUv1Values[index * 2 + 1] : 0.0f;
    }

    if (invalidVertexCount >= previewVertexIndices.size()) {
        if (outError != nullptr) {
            *outError = "animated preview produced only invalid vertices.";
        }
        return false;
    }
    return true;
}

namespace {

struct MainProgramHandleSet {
    GLint program = 0;
    GLint position = -1;
    GLint normal = -1;
    GLint texCoord = -1;
    GLint addUv1 = -1;
    GLint mvpMatrix = -1;
    GLint modelViewMatrix = -1;
    GLint baseTexture = -1;
    GLint sphereTexture = -1;
    GLint toonTexture = -1;
    GLint useBaseTexture = -1;
    GLint useSphereTexture = -1;
    GLint useToonTexture = -1;
    GLint sphereMode = -1;
    GLint diffuseColor = -1;
    GLint ambientColor = -1;
    GLint specularColor = -1;
    GLint specularPower = -1;
    GLint textureMulFactor = -1;
    GLint textureAddFactor = -1;
    GLint sphereMulFactor = -1;
    GLint sphereAddFactor = -1;
    GLint toonMulFactor = -1;
    GLint toonAddFactor = -1;
};

struct EdgeProgramHandleSet {
    GLint program = 0;
    GLint position = -1;
    GLint normal = -1;
    GLint mvpMatrix = -1;
    GLint edgeColor = -1;
    GLint edgeSize = -1;
    GLint edgeScale = -1;
};

struct RenderSegment {
    int materialIndex = -1;
    GLint firstVertex = 0;
    GLsizei vertexCount = 0;
    GLint baseTextureSlot = -1;
    GLint sphereTextureSlot = -1;
    GLint toonTextureSlot = -1;
    PreviewSphereMode sphereMode = PreviewSphereMode::None;
    float depth = 0.0f;
};

bool ReadHandleArray(JNIEnv* env, jintArray array, std::vector<jint>* outValues, std::string* outError) {
    if (array == nullptr || outValues == nullptr) {
        if (outError != nullptr) {
            *outError = "native render handle array is null.";
        }
        return false;
    }

    const jsize length = env->GetArrayLength(array);
    if (length <= 0) {
        if (outError != nullptr) {
            *outError = "native render handle array is empty.";
        }
        return false;
    }

    outValues->resize(static_cast<size_t>(length));
    env->GetIntArrayRegion(array, 0, length, outValues->data());
    return true;
}

bool ParseMainProgramHandles(JNIEnv* env, jintArray array, MainProgramHandleSet* outHandles, std::string* outError) {
    std::vector<jint> values;
    if (!ReadHandleArray(env, array, &values, outError)) {
        return false;
    }
    if (values.size() != 24) {
        if (outError != nullptr) {
            *outError = "invalid main render handle array size.";
        }
        return false;
    }

    outHandles->program = values[0];
    outHandles->position = values[1];
    outHandles->normal = values[2];
    outHandles->texCoord = values[3];
    outHandles->addUv1 = values[4];
    outHandles->mvpMatrix = values[5];
    outHandles->modelViewMatrix = values[6];
    outHandles->baseTexture = values[7];
    outHandles->sphereTexture = values[8];
    outHandles->toonTexture = values[9];
    outHandles->useBaseTexture = values[10];
    outHandles->useSphereTexture = values[11];
    outHandles->useToonTexture = values[12];
    outHandles->sphereMode = values[13];
    outHandles->diffuseColor = values[14];
    outHandles->ambientColor = values[15];
    outHandles->specularColor = values[16];
    outHandles->specularPower = values[17];
    outHandles->textureMulFactor = values[18];
    outHandles->textureAddFactor = values[19];
    outHandles->sphereMulFactor = values[20];
    outHandles->sphereAddFactor = values[21];
    outHandles->toonMulFactor = values[22];
    outHandles->toonAddFactor = values[23];
    return outHandles->program > 0;
}

bool ParseEdgeProgramHandles(JNIEnv* env, jintArray array, EdgeProgramHandleSet* outHandles, std::string* outError) {
    std::vector<jint> values;
    if (!ReadHandleArray(env, array, &values, outError)) {
        return false;
    }
    if (values.size() != 7) {
        if (outError != nullptr) {
            *outError = "invalid edge render handle array size.";
        }
        return false;
    }

    outHandles->program = values[0];
    outHandles->position = values[1];
    outHandles->normal = values[2];
    outHandles->mvpMatrix = values[3];
    outHandles->edgeColor = values[4];
    outHandles->edgeSize = values[5];
    outHandles->edgeScale = values[6];
    return outHandles->program > 0;
}

GLuint TextureIdAt(const jint* values, jsize count, int slot) {
    if (values == nullptr || slot < 0 || slot >= count) {
        return 0;
    }
    return static_cast<GLuint>(values[slot]);
}

bool TextureHasAlphaAt(const jint* values, jsize count, int slot) {
    if (values == nullptr || slot < 0 || slot >= count) {
        return false;
    }
    return (values[slot] & kTextureFlagHasAlpha) != 0;
}

float ComputeSegmentDepth(const float* drawVertexData, const RenderSegment& segment, const glm::mat4& modelViewMat) {
    if (drawVertexData == nullptr || segment.vertexCount <= 0) {
        return 0.0f;
    }

    const int sampleCount = std::min<int>(segment.vertexCount, 3);
    float depthSum = 0.0f;
    for (int index = 0; index < sampleCount; ++index) {
        const size_t base = static_cast<size_t>(segment.firstVertex + index) * kPreviewVertexStride;
        const glm::vec4 viewPosition = modelViewMat * glm::vec4(
            drawVertexData[base],
            drawVertexData[base + 1],
            drawVertexData[base + 2],
            1.0f
        );
        depthSum += viewPosition.z;
    }
    return depthSum / static_cast<float>(sampleCount);
}

const saba::MMDMaterial* ResolveMaterial(const saba::MMDMaterial* materials, size_t materialCount, int materialIndex) {
    if (materials == nullptr || materialIndex < 0 || static_cast<size_t>(materialIndex) >= materialCount) {
        return nullptr;
    }
    return materials + materialIndex;
}

}

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
) {
    if (drawVertexData == nullptr || vertexCount <= 0) {
        if (outError != nullptr) {
            *outError = "native preview draw vertex data is empty.";
        }
        return false;
    }

    MainProgramHandleSet mainHandles;
    EdgeProgramHandleSet edgeHandles;
    if (!ParseMainProgramHandles(env, mainProgramHandles, &mainHandles, outError) ||
        !ParseEdgeProgramHandles(env, edgeProgramHandles, &edgeHandles, outError)) {
        return false;
    }

    const auto* segmentValues = static_cast<const jint*>(
        materialSegmentsBuffer != nullptr ? env->GetDirectBufferAddress(materialSegmentsBuffer) : nullptr
    );
    if (segmentValues == nullptr) {
        if (outError != nullptr) {
            *outError = "failed to access preview material segment direct buffer.";
        }
        return false;
    }

    const jlong segmentCapacity = env->GetDirectBufferCapacity(materialSegmentsBuffer);
    if (segmentCapacity <= 0 || segmentCapacity % kPreviewMaterialSegmentStride != 0) {
        if (outError != nullptr) {
            *outError = "preview material segment buffer has invalid capacity.";
        }
        return false;
    }

    const auto* textureValues = static_cast<const jint*>(
        textureIdsBySlotBuffer != nullptr ? env->GetDirectBufferAddress(textureIdsBySlotBuffer) : nullptr
    );
    const auto* textureFlagValues = static_cast<const jint*>(
        textureFlagsBySlotBuffer != nullptr ? env->GetDirectBufferAddress(textureFlagsBySlotBuffer) : nullptr
    );
    const jsize textureValueCount = textureIdsBySlotBuffer != nullptr ? static_cast<jsize>(env->GetDirectBufferCapacity(textureIdsBySlotBuffer)) : 0;
    const jsize textureFlagCount = textureFlagsBySlotBuffer != nullptr ? static_cast<jsize>(env->GetDirectBufferCapacity(textureFlagsBySlotBuffer)) : 0;

    const glm::mat4 modelViewMat = viewMat * modelMat;
    const glm::mat4 mvpMat = projectionMat * modelViewMat;
    const float edgeScale = std::max(cameraDistance, 1.0f) * 0.004f / std::max(fitScale, 0.0001f);
    const GLsizei strideBytes = static_cast<GLsizei>(kPreviewVertexStride * sizeof(float));

    std::vector<RenderSegment> opaqueSegments;
    std::vector<RenderSegment> transparentSegments;
    opaqueSegments.reserve(static_cast<size_t>(segmentCapacity / kPreviewMaterialSegmentStride));
    transparentSegments.reserve(static_cast<size_t>(segmentCapacity / kPreviewMaterialSegmentStride));

    for (jsize cursor = 0; cursor + 6 < segmentCapacity; cursor += kPreviewMaterialSegmentStride) {
        RenderSegment segment;
        segment.materialIndex = segmentValues[cursor];
        segment.firstVertex = segmentValues[cursor + 1];
        segment.vertexCount = static_cast<GLsizei>(segmentValues[cursor + 2]);
        segment.baseTextureSlot = segmentValues[cursor + 3];
        segment.sphereTextureSlot = segmentValues[cursor + 4];
        segment.toonTextureSlot = segmentValues[cursor + 5];
        segment.sphereMode = static_cast<PreviewSphereMode>(segmentValues[cursor + 6]);

        if (segment.firstVertex < 0 || segment.vertexCount <= 0 || segment.firstVertex >= vertexCount) {
            continue;
        }
        segment.vertexCount = std::min(segment.vertexCount, static_cast<GLsizei>(vertexCount - segment.firstVertex));
        if (segment.vertexCount <= 0) {
            continue;
        }

        const saba::MMDMaterial* material = ResolveMaterial(materials, materialCount, segment.materialIndex);
        if (material == nullptr) {
            continue;
        }
        if (material->m_alpha <= 0.001f) {
            continue;
        }

        const bool transparent =
            material->m_alpha < 0.999f ||
            TextureHasAlphaAt(textureFlagValues, textureFlagCount, segment.baseTextureSlot);
        segment.depth = ComputeSegmentDepth(drawVertexData, segment, modelViewMat);
        if (transparent) {
            transparentSegments.push_back(segment);
        } else {
            opaqueSegments.push_back(segment);
        }
    }

    std::sort(transparentSegments.begin(), transparentSegments.end(), [](const RenderSegment& lhs, const RenderSegment& rhs) {
        return lhs.depth < rhs.depth;
    });

    glUseProgram(static_cast<GLuint>(mainHandles.program));
    glUniformMatrix4fv(mainHandles.mvpMatrix, 1, GL_FALSE, glm::value_ptr(mvpMat));
    glUniformMatrix4fv(mainHandles.modelViewMatrix, 1, GL_FALSE, glm::value_ptr(modelViewMat));
    glUniform1i(mainHandles.baseTexture, 0);
    glUniform1i(mainHandles.sphereTexture, 1);
    glUniform1i(mainHandles.toonTexture, 2);

    glVertexAttribPointer(mainHandles.position, 3, GL_FLOAT, GL_FALSE, strideBytes, reinterpret_cast<const GLvoid*>(drawVertexData));
    glEnableVertexAttribArray(mainHandles.position);
    glVertexAttribPointer(mainHandles.normal, 3, GL_FLOAT, GL_FALSE, strideBytes, reinterpret_cast<const GLvoid*>(drawVertexData + 3));
    glEnableVertexAttribArray(mainHandles.normal);
    glVertexAttribPointer(mainHandles.texCoord, 2, GL_FLOAT, GL_FALSE, strideBytes, reinterpret_cast<const GLvoid*>(drawVertexData + 6));
    glEnableVertexAttribArray(mainHandles.texCoord);
    glVertexAttribPointer(mainHandles.addUv1, 2, GL_FLOAT, GL_FALSE, strideBytes, reinterpret_cast<const GLvoid*>(drawVertexData + 8));
    glEnableVertexAttribArray(mainHandles.addUv1);

    GLuint boundBaseTexture = static_cast<GLuint>(-1);
    GLuint boundSphereTexture = static_cast<GLuint>(-1);
    GLuint boundToonTexture = static_cast<GLuint>(-1);

    auto drawMainPass = [&](const RenderSegment& segment) {
        const saba::MMDMaterial* material = ResolveMaterial(materials, materialCount, segment.materialIndex);
        if (material == nullptr) {
            return;
        }

        if (material->m_bothFace) {
            glDisable(GL_CULL_FACE);
        } else {
            glEnable(GL_CULL_FACE);
            glCullFace(GL_BACK);
        }

        const GLuint baseTextureId = TextureIdAt(textureValues, textureValueCount, segment.baseTextureSlot);
        const GLuint sphereTextureId = TextureIdAt(textureValues, textureValueCount, segment.sphereTextureSlot);
        const GLuint toonTextureId = TextureIdAt(textureValues, textureValueCount, segment.toonTextureSlot);

        glActiveTexture(GL_TEXTURE0);
        if (baseTextureId != boundBaseTexture) {
            glBindTexture(GL_TEXTURE_2D, baseTextureId);
            boundBaseTexture = baseTextureId;
        }
        glActiveTexture(GL_TEXTURE1);
        if (sphereTextureId != boundSphereTexture) {
            glBindTexture(GL_TEXTURE_2D, sphereTextureId);
            boundSphereTexture = sphereTextureId;
        }
        glActiveTexture(GL_TEXTURE2);
        if (toonTextureId != boundToonTexture) {
            glBindTexture(GL_TEXTURE_2D, toonTextureId);
            boundToonTexture = toonTextureId;
        }

        glUniform1f(mainHandles.useBaseTexture, baseTextureId != 0 ? 1.0f : 0.0f);
        glUniform1f(mainHandles.useSphereTexture, sphereTextureId != 0 ? 1.0f : 0.0f);
        glUniform1f(mainHandles.useToonTexture, toonTextureId != 0 ? 1.0f : 0.0f);
        glUniform1f(mainHandles.sphereMode, sphereTextureId != 0 ? static_cast<float>(segment.sphereMode) : 0.0f);
        glUniform4f(mainHandles.diffuseColor, material->m_diffuse.r, material->m_diffuse.g, material->m_diffuse.b, material->m_alpha);
        glUniform3f(mainHandles.ambientColor, material->m_ambient.r, material->m_ambient.g, material->m_ambient.b);
        glUniform3f(mainHandles.specularColor, material->m_specular.r, material->m_specular.g, material->m_specular.b);
        glUniform1f(mainHandles.specularPower, material->m_specularPower);
        glUniform4f(mainHandles.textureMulFactor, material->m_textureMulFactor.r, material->m_textureMulFactor.g, material->m_textureMulFactor.b, material->m_textureMulFactor.a);
        glUniform4f(mainHandles.textureAddFactor, material->m_textureAddFactor.r, material->m_textureAddFactor.g, material->m_textureAddFactor.b, material->m_textureAddFactor.a);
        glUniform4f(mainHandles.sphereMulFactor, material->m_spTextureMulFactor.r, material->m_spTextureMulFactor.g, material->m_spTextureMulFactor.b, material->m_spTextureMulFactor.a);
        glUniform4f(mainHandles.sphereAddFactor, material->m_spTextureAddFactor.r, material->m_spTextureAddFactor.g, material->m_spTextureAddFactor.b, material->m_spTextureAddFactor.a);
        glUniform4f(mainHandles.toonMulFactor, material->m_toonTextureMulFactor.r, material->m_toonTextureMulFactor.g, material->m_toonTextureMulFactor.b, material->m_toonTextureMulFactor.a);
        glUniform4f(mainHandles.toonAddFactor, material->m_toonTextureAddFactor.r, material->m_toonTextureAddFactor.g, material->m_toonTextureAddFactor.b, material->m_toonTextureAddFactor.a);
        glDrawArrays(GL_TRIANGLES, segment.firstVertex, segment.vertexCount);
    };

    for (const auto& segment : opaqueSegments) {
        drawMainPass(segment);
    }
    glDepthMask(GL_FALSE);
    for (const auto& segment : transparentSegments) {
        drawMainPass(segment);
    }
    glDepthMask(GL_TRUE);

    glDisableVertexAttribArray(mainHandles.position);
    glDisableVertexAttribArray(mainHandles.normal);
    glDisableVertexAttribArray(mainHandles.texCoord);
    glDisableVertexAttribArray(mainHandles.addUv1);

    glUseProgram(static_cast<GLuint>(edgeHandles.program));
    glUniformMatrix4fv(edgeHandles.mvpMatrix, 1, GL_FALSE, glm::value_ptr(mvpMat));
    glUniform1f(edgeHandles.edgeScale, edgeScale);

    glVertexAttribPointer(edgeHandles.position, 3, GL_FLOAT, GL_FALSE, strideBytes, reinterpret_cast<const GLvoid*>(drawVertexData));
    glEnableVertexAttribArray(edgeHandles.position);
    glVertexAttribPointer(edgeHandles.normal, 3, GL_FLOAT, GL_FALSE, strideBytes, reinterpret_cast<const GLvoid*>(drawVertexData + 3));
    glEnableVertexAttribArray(edgeHandles.normal);

    auto drawEdgePass = [&](const RenderSegment& segment) {
        const saba::MMDMaterial* material = ResolveMaterial(materials, materialCount, segment.materialIndex);
        if (material == nullptr || material->m_edgeFlag == 0 || material->m_edgeSize <= 0.0f) {
            return;
        }

        const float edgeAlpha = material->m_edgeColor.a * material->m_alpha;
        if (edgeAlpha <= 0.001f) {
            return;
        }

        glEnable(GL_CULL_FACE);
        glCullFace(GL_FRONT);
        glUniform4f(edgeHandles.edgeColor, material->m_edgeColor.r, material->m_edgeColor.g, material->m_edgeColor.b, edgeAlpha);
        glUniform1f(edgeHandles.edgeSize, material->m_edgeSize);
        glDrawArrays(GL_TRIANGLES, segment.firstVertex, segment.vertexCount);
    };

    for (const auto& segment : opaqueSegments) {
        drawEdgePass(segment);
    }
    glDepthMask(GL_FALSE);
    for (const auto& segment : transparentSegments) {
        drawEdgePass(segment);
    }
    glDepthMask(GL_TRUE);

    glDisableVertexAttribArray(edgeHandles.position);
    glDisableVertexAttribArray(edgeHandles.normal);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, 0);
    glActiveTexture(GL_TEXTURE1);
    glBindTexture(GL_TEXTURE_2D, 0);
    glActiveTexture(GL_TEXTURE2);
    glBindTexture(GL_TEXTURE_2D, 0);
    glDisable(GL_CULL_FACE);
    glUseProgram(0);
    return true;
}

}
#endif
