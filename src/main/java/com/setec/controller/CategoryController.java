package com.setec.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.setec.entities.Category;
import com.setec.repos.CategoryRepos;

@RestController
@RequestMapping("/api/category")
public class CategoryController {

	private final CategoryRepos categoryRepos;

	public CategoryController(CategoryRepos categoryRepos) {
		this.categoryRepos = categoryRepos;
	}

	// 1. Get all categories
	@GetMapping
	public Object getAll() {
		return categoryRepos.findAll();
	}

	// 2. Get single category by ID
	@GetMapping("{id}")
	public Object getById(@PathVariable("id") Integer id) {
		var cate = categoryRepos.findById(id);

		if (cate.isPresent()) {
			return cate.get();
		}

		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(Map.of("Message", "Category ID " + id + " Not Found"));
	}

	// 3. Create a new category
	@PostMapping
	public Object postCategory(@RequestBody Category category) {
		try {
			Category saved = categoryRepos.save(category);
			return ResponseEntity.status(HttpStatus.CREATED).body(saved);
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(Map.of("Message", "Failed to create category: " + e.getMessage()));
		}
	}

	// 4. Update an existing category
	@PutMapping("{id}")
	public Object updateCategory(@PathVariable("id") Integer id, @RequestBody Map<String, String> body) {
		var existing = categoryRepos.findById(id);
		if (existing.isEmpty()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(Map.of("Message", "Category ID " + id + " Not Found"));
		}

		String newName = body.get("name");
		if (newName == null || newName.trim().isEmpty()) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(Map.of("Message", "Category name is required"));
		}

		Category cate = existing.get();
		cate.setName(newName.trim());
		categoryRepos.save(cate);

		return ResponseEntity.ok(cate);
	}

	// 5. Delete category by ID
	@DeleteMapping("{id}")
	public Object deleteCategory(@PathVariable("id") Integer id) {
		var cate = categoryRepos.findById(id);
		if (cate.isPresent()) {
			categoryRepos.deleteById(id);
			return ResponseEntity.ok(Map.of("Message", "Category ID " + id + " deleted successfully"));
		}

		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(Map.of("Message", "Category ID " + id + " Not Found"));
	}
}