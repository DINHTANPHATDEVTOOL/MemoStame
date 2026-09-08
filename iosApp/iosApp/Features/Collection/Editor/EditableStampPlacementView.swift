import SwiftUI

private let SelectionGold = Color(red: 0.82, green: 0.65, blue: 0.35)
private let SelectionOutlineColor = Color(red: 0.78, green: 0.61, blue: 0.24)

public struct EditableStampPlacementView: View {
    public let placement: PersistedStampPlacementData
    public let stamp: BookStampItem
    public let pageWidth: CGFloat
    public let pageHeight: CGFloat
    @ObservedObject public var editState: AlbumEditState
    public var onStampClick: (String) -> Void

    @State private var dragOffset: CGSize = .zero
    @State private var currentScaleDelta: CGFloat = 1.0
    @State private var currentRotationDelta: Angle = .zero

    public init(
        placement: PersistedStampPlacementData,
        stamp: BookStampItem,
        pageWidth: CGFloat,
        pageHeight: CGFloat,
        editState: AlbumEditState,
        onStampClick: @escaping (String) -> Void = { _ in }
    ) {
        self.placement = placement
        self.stamp = stamp
        self.pageWidth = pageWidth
        self.pageHeight = pageHeight
        self.editState = editState
        self.onStampClick = onStampClick
    }

    private var isSelected: Bool {
        editState.selectedPlacementId == placement.id
    }

    private var isEditMode: Bool {
        editState.mode == .edit
    }

    public var body: some View {
        let transform = editState.getResolvedTransform(for: placement)

        let baseWidth: CGFloat = max(84.0 * CGFloat(transform.scale), 44.0)
        let baseHeight: CGFloat = max(104.0 * CGFloat(transform.scale), 44.0)
        let posX: CGFloat = pageWidth * CGFloat(transform.x)
        let posY: CGFloat = pageHeight * CGFloat(transform.y)

        ZStack {
            // Main Stamp Cell Content
            BookStampCellView(stamp: stamp, onClick: {
                if isEditMode {
                    editState.selectPlacement(placement.id)
                } else {
                    onStampClick(stamp.id)
                }
            })
            .frame(width: baseWidth, height: baseHeight)

            // Professional Selection Frame
            if isSelected && isEditMode {
                RoundedRectangle(cornerRadius: 6)
                    .stroke(SelectionOutlineColor, lineWidth: 2)
                    .frame(width: baseWidth + 6, height: baseHeight + 6)

                // 4 Corner dots
                cornerDots(width: baseWidth + 6, height: baseHeight + 6)
            }
        }
        .rotationEffect(.degrees(transform.rotationDegrees))
        .position(x: posX, y: posY)
        .zIndex(Double(transform.zIndex) + (isSelected ? 100.0 : 0.0))
        .simultaneousGesture(
            isEditMode ? combinedGesture(transform: transform) : nil
        )
    }

    @ViewBuilder
    private func cornerDots(width: CGFloat, height: CGFloat) -> some View {
        let halfW = width / 2.0
        let halfH = height / 2.0

        Circle()
            .fill(SelectionGold)
            .frame(width: 8, height: 8)
            .overlay(Circle().stroke(Color.white, lineWidth: 1))
            .offset(x: -halfW, y: -halfH)

        Circle()
            .fill(SelectionGold)
            .frame(width: 8, height: 8)
            .overlay(Circle().stroke(Color.white, lineWidth: 1))
            .offset(x: halfW, y: -halfH)

        Circle()
            .fill(SelectionGold)
            .frame(width: 8, height: 8)
            .overlay(Circle().stroke(Color.white, lineWidth: 1))
            .offset(x: -halfW, y: halfH)

        Circle()
            .fill(SelectionGold)
            .frame(width: 8, height: 8)
            .overlay(Circle().stroke(Color.white, lineWidth: 1))
            .offset(x: halfW, y: halfH)
    }

    private func combinedGesture(transform: TransientPlacementTransformData) -> some Gesture {
        let drag = DragGesture(minimumDistance: 1)
            .onChanged { value in
                if !isSelected {
                    editState.selectPlacement(placement.id)
                }
                let normDx = Double(value.translation.width / max(pageWidth, 1.0))
                let normDy = Double(value.translation.height / max(pageHeight, 1.0))

                editState.updateTransientTransform(
                    placementId: placement.id,
                    x: placement.x + normDx,
                    y: placement.y + normDy,
                    scale: transform.scale,
                    rotationDegrees: transform.rotationDegrees,
                    zIndex: transform.zIndex
                )
            }
            .onEnded { _ in
                editState.commitTransform(placementId: placement.id, originalPlacement: placement)
            }

        let pinch = MagnificationGesture()
            .onChanged { scaleDelta in
                if !isSelected {
                    editState.selectPlacement(placement.id)
                }
                editState.updateTransientTransform(
                    placementId: placement.id,
                    x: transform.x,
                    y: transform.y,
                    scale: placement.scale * Double(scaleDelta),
                    rotationDegrees: transform.rotationDegrees,
                    zIndex: transform.zIndex
                )
            }
            .onEnded { _ in
                editState.commitTransform(placementId: placement.id, originalPlacement: placement)
            }

        let rotate = RotationGesture()
            .onChanged { angleDelta in
                if !isSelected {
                    editState.selectPlacement(placement.id)
                }
                editState.updateTransientTransform(
                    placementId: placement.id,
                    x: transform.x,
                    y: transform.y,
                    scale: transform.scale,
                    rotationDegrees: placement.rotationDegrees + angleDelta.degrees,
                    zIndex: transform.zIndex
                )
            }
            .onEnded { _ in
                editState.commitTransform(placementId: placement.id, originalPlacement: placement)
            }

        return SimultaneousGesture(SimultaneousGesture(drag, pinch), rotate)
    }
}
