#!/bin/bash
OUTPUT_FILE="memostamp_codebase_summary.md"
echo "# MemoStamp Codebase Summary for Google AI Studio" > $OUTPUT_FILE
echo "Generated on $(date)" >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE

echo "## File Structure & Source Files" >> $OUTPUT_FILE

find shared/src androidApp/src/main iosApp/iosApp -type f \( -name "*.kt" -o -name "*.swift" -o -name "*.kts" \) | sort | while read -r file; do
    echo "---" >> $OUTPUT_FILE
    echo "### File: $file" >> $OUTPUT_FILE
    echo '```' >> $OUTPUT_FILE
    cat "$file" >> $OUTPUT_FILE
    echo "" >> $OUTPUT_FILE
    echo '```' >> $OUTPUT_FILE
    echo "" >> $OUTPUT_FILE
done

echo "Codebase packed successfully into $OUTPUT_FILE"
