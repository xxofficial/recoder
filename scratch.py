import urllib.request
import re

def generate_kotlin_map():
    url = "https://www.unicode.org/Public/UNIDATA/CJKRadicals.txt"
    req = urllib.request.Request(
        url, 
        headers={'User-Agent': 'Mozilla/5.0'}
    )
    
    try:
        with urllib.request.urlopen(req) as response:
            content = response.read().decode('utf-8')
    except Exception as e:
        print(f"Failed to fetch: {e}")
        return

    mappings = {}
    for line in content.splitlines():
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        
        # Format: Index; Radical; Unified
        parts = [p.strip() for p in line.split(';')]
        if len(parts) >= 3:
            rad_hex = parts[1]
            uni_hex = parts[2]
            
            # Convert hex string to integer
            try:
                rad_val = int(rad_hex, 16)
                uni_val = int(uni_hex, 16)
                mappings[rad_val] = uni_val
            except ValueError:
                pass

    # Write Kotlin code to file
    with open("mapping.txt", "w", encoding="utf-8") as f:
        f.write("val CJK_RADICALS_MAP = mapOf(\n")
        for rad, uni in sorted(mappings.items()):
            rad_char = chr(rad)
            uni_char = chr(uni)
            f.write(f"    0x{rad:04X} to 0x{uni:04X}, // {rad_char} ({hex(rad)}) -> {uni_char} ({hex(uni)})\n")
        f.write(")\n")
    print("Successfully generated mapping.txt!")

if __name__ == "__main__":
    generate_kotlin_map()
