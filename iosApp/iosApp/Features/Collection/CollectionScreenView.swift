import SwiftUI
#if canImport(UIKit)
import UIKit
#endif
import shared

struct CollectionScreenView: View {
    let repository: SharedMemoStampRepository
    @State private var selectedCollection: CollectionItem? = nil

    let columns = [
        GridItem(.flexible(), spacing: 14),
        GridItem(.flexible(), spacing: 14)
    ]

    var collections: [CollectionItem] {
        (repository.collections.value as? [CollectionItem]) ?? []
    }

    private func colIconName(_ key: String) -> String {
        switch key {
        case "coffee", "☕": return "cup.and.saucer.fill"
        case "plane", "✈️": return "paperplane.fill"
        case "tree", "🌲": return "leaf.fill"
        case "palette", "🎨": return "paintpalette.fill"
        default: return "folder.fill"
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("STAMP ALBUMS")
                        .font(.title2.bold())
                        .foregroundColor(MSColors.ink)
                    Text("Curated Memory Collections")
                        .font(.caption)
                        .foregroundColor(MSColors.grey)
                }
                Spacer()
            }
            .padding()

            Divider()

            ScrollView {
                LazyVGrid(columns: columns, spacing: 14) {
                    ForEach(collections, id: \.id) { col in
                        VStack(alignment: .leading, spacing: 10) {
                            HStack {
                                Image(systemName: colIconName(col.iconEmoji))
                                    .font(.system(size: 26))
                                    .foregroundColor(MSColors.stamp)
                                Spacer()
                                Text("\(col.stampsCount)/\(col.targetCount)")
                                    .font(.caption2.bold())
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(MSColors.stamp.opacity(0.15))
                                    .foregroundColor(MSColors.stamp)
                                    .cornerRadius(10)
                            }

                            Text(col.name)
                                .font(.headline.bold())
                                .foregroundColor(MSColors.ink)

                            let desc = col.description_ ?? ""
                            if !desc.isEmpty {
                                Text(desc)
                                    .font(.caption)
                                    .foregroundColor(MSColors.grey)
                                    .lineLimit(2)
                            }

                            ProgressView(value: Double(col.stampsCount), total: Double(col.targetCount))
                                .accentColor(MSColors.gold)
                        }
                        .padding(14)
                        .background(Color.white)
                        .cornerRadius(16)
                        .shadow(color: Color.black.opacity(0.04), radius: 4, x: 0, y: 2)
                        .onTapGesture {
                            selectedCollection = col
                        }
                    }
                }
                .padding()
            }
        }
        .background(MSColors.paper.ignoresSafeArea())
        .sheet(item: $selectedCollection) { col in
            VStack {
                Text(col.iconEmoji + " " + col.name)
                    .font(.title2.bold())
                    .padding()
                Text("Collection detail containing \(col.stampsCount) stamps")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                Spacer()
            }
        }
    }
}
