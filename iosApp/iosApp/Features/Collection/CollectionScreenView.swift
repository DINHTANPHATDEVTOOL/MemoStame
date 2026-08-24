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

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("STAMP ALBUMS")
                        .font(.title2.bold())
                        .foregroundColor(Color(red: 0.15, green: 0.15, blue: 0.18))
                    Text("Curated Memory Collections")
                        .font(.caption)
                        .foregroundColor(.secondary)
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
                                Text(col.iconEmoji)
                                    .font(.system(size: 36))
                                Spacer()
                                Text("\(col.stampsCount)/\(col.targetCount)")
                                    .font(.caption2.bold())
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(Color(red: 0.85, green: 0.25, blue: 0.20).opacity(0.15))
                                    .foregroundColor(Color(red: 0.85, green: 0.25, blue: 0.20))
                                    .cornerRadius(10)
                            }

                            Text(col.name)
                                .font(.headline.bold())
                                .foregroundColor(Color(red: 0.15, green: 0.15, blue: 0.18))

                            let desc = col.description_ ?? ""
                            if !desc.isEmpty {
                                Text(desc)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                    .lineLimit(2)
                            }

                            ProgressView(value: Double(col.stampsCount), total: Double(col.targetCount))
                                .accentColor(Color(red: 0.82, green: 0.65, blue: 0.35))
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
